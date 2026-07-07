/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.izylife.izykube.services;

import com.izylife.izykube.configuration.GitOpsProperties;
import com.izylife.izykube.factory.ClientFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitOpsDeploymentService {

    private static final String ARGO_APP_RESOURCES_FINALIZER = "resources-finalizer.argocd.argoproj.io";

    private final ClientFactory clientFactory;
    private final GitOpsProperties gitOpsProperties;

    public boolean isEnabled() {
        return gitOpsProperties.isEnabled();
    }

    public void deployCluster(String clusterId, String namespace, List<String> yamlList) {
        if (yamlList == null || yamlList.isEmpty()) {
            log.warn("No manifests to push for cluster {}", clusterId);
            return;
        }

        withRepo(clusterId, namespace, yamlList, false);
        ensureArgoApplication(namespace);
    }

    public void undeployCluster(String clusterId, String namespace) {
        withRepo(clusterId, namespace, List.of(), true);
        deleteArgoApplication(namespace);
    }

    private void withRepo(String clusterId, String namespace, List<String> yamlList, boolean removeOnly) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("izykube-gitops-");
            boolean remoteIsEmpty = isRemoteRepoEmpty();

            Git git;
            if (remoteIsEmpty) {
                git = Git.init().setDirectory(workDir.toFile()).call();
                git.remoteAdd().setName("origin").setUri(new URIish(gitOpsProperties.getRepoUrl())).call();
                git.checkout().setCreateBranch(true).setName(gitOpsProperties.getBranch()).call();
            } else {
                git = Git.cloneRepository()
                        .setURI(gitOpsProperties.getRepoUrl())
                        .setDirectory(workDir.toFile())
                        .setCredentialsProvider(credentialsProvider())
                        .call();
                checkoutBranch(git);
            }

            Path appDir = workDir.resolve(gitOpsProperties.getBasePath()).resolve(namespace);
            if (removeOnly) {
                deletePathIfExists(appDir);
            } else {
                Files.createDirectories(appDir);
                cleanYamlFiles(appDir);
                for (int i = 0; i < yamlList.size(); i++) {
                    String fileName = String.format("%03d-resource.yaml", i + 1);
                    Files.writeString(appDir.resolve(fileName), yamlList.get(i), StandardCharsets.UTF_8);
                }
            }

            git.add().addFilepattern(".").call();
            if (!git.status().call().isClean()) {
                git.commit()
                        .setMessage(commitMessage(clusterId, namespace, removeOnly))
                        .setAuthor(new PersonIdent(gitOpsProperties.getCommitAuthorName(), gitOpsProperties.getCommitAuthorEmail(), java.util.Date.from(Instant.now()), java.util.TimeZone.getDefault()))
                        .call();

                if (remoteIsEmpty) {
                    git.push()
                            .setCredentialsProvider(credentialsProvider())
                            .setRemote("origin")
                            .setRefSpecs(new RefSpec(gitOpsProperties.getBranch() + ":" + gitOpsProperties.getBranch()))
                            .call();
                } else {
                    git.push().setCredentialsProvider(credentialsProvider()).call();
                }
            }
            git.close();
        } catch (Exception e) {
            throw new RuntimeException("GitOps push failed: " + e.getMessage(), e);
        } finally {
            if (workDir != null) {
                try {
                    deletePathIfExists(workDir);
                } catch (IOException ex) {
                    log.warn("Unable to cleanup temporary git directory {}", workDir);
                }
            }
        }
    }

    private void ensureArgoApplication(String namespace) {
        String appName = appName(namespace);
        String appYaml = """
                apiVersion: argoproj.io/v1alpha1
                kind: Application
                metadata:
                  name: %s
                  namespace: %s
                  finalizers:
                    - %s
                spec:
                  project: %s
                  source:
                    repoURL: %s
                    targetRevision: %s
                    path: %s/%s
                  destination:
                    server: https://kubernetes.default.svc
                    namespace: %s
                """.formatted(
                appName,
                gitOpsProperties.getArgoCdNamespace(),
                ARGO_APP_RESOURCES_FINALIZER,
                gitOpsProperties.getArgoCdProject(),
                argoRepoUrl(),
                gitOpsProperties.getBranch(),
                gitOpsProperties.getBasePath(),
                namespace,
                namespace
        );

        if (gitOpsProperties.isAutoSync()) {
            appYaml += """
                    syncPolicy:
                      automated:
                        prune: %s
                        selfHeal: %s
                      syncOptions:
                        - CreateNamespace=true
                    """.formatted(gitOpsProperties.isPrune(), gitOpsProperties.isSelfHeal());
        }

        KubernetesClient kubernetesClient = (KubernetesClient) clientFactory.getClient("kubernetes");
        List<HasMetadata> resources = kubernetesClient.load(new ByteArrayInputStream(appYaml.getBytes(StandardCharsets.UTF_8))).items();
        for (HasMetadata resource : resources) {
            kubernetesClient.resource(resource).createOrReplace();
        }
    }

    private void deleteArgoApplication(String namespace) {
        KubernetesClient kubernetesClient = (KubernetesClient) clientFactory.getClient("kubernetes");
        var appResource = kubernetesClient.genericKubernetesResources("argoproj.io/v1alpha1", "Application")
                .inNamespace(gitOpsProperties.getArgoCdNamespace())
                .withName(appName(namespace));

        GenericKubernetesResource existing = appResource.get();
        if (existing != null) {
            ensureResourcesFinalizer(existing);
            appResource.createOrReplace(existing);
            appResource.delete();
        }
    }

    private void ensureResourcesFinalizer(GenericKubernetesResource app) {
        ObjectMeta metadata = app.getMetadata();
        if (metadata == null) {
            metadata = new ObjectMeta();
            app.setMetadata(metadata);
        }
        List<String> finalizers = metadata.getFinalizers();
        if (finalizers == null || finalizers.isEmpty()) {
            metadata.setFinalizers(new java.util.ArrayList<>(List.of(ARGO_APP_RESOURCES_FINALIZER)));
            return;
        }
        if (!finalizers.contains(ARGO_APP_RESOURCES_FINALIZER)) {
            finalizers.add(ARGO_APP_RESOURCES_FINALIZER);
        }
    }

    private void checkoutBranch(Git git) {
        try {
            git.checkout()
                    .setName(gitOpsProperties.getBranch())
                    .setCreateBranch(true)
                    .setStartPoint("origin/" + gitOpsProperties.getBranch())
                    .call();
        } catch (Exception e) {
            try {
                git.checkout().setName(gitOpsProperties.getBranch()).call();
            } catch (Exception ignored) {
                throw new RuntimeException("Cannot checkout branch " + gitOpsProperties.getBranch(), ignored);
            }
        }
    }

    private boolean isRemoteRepoEmpty() {
        try {
            LsRemoteCommand lsRemote = Git.lsRemoteRepository();
            lsRemote.setRemote(gitOpsProperties.getRepoUrl());
            lsRemote.setHeads(true);
            CredentialsProvider cp = credentialsProvider();
            if (cp != null) {
                lsRemote.setCredentialsProvider(cp);
            }
            return lsRemote.call().isEmpty();
        } catch (Exception e) {
            throw new RuntimeException("Cannot inspect remote git repository: " + e.getMessage(), e);
        }
    }

    private CredentialsProvider credentialsProvider() {
        String username = gitOpsProperties.getUsername();
        String password = gitOpsProperties.getPassword();
        if (username == null || username.isBlank()) {
            return null;
        }
        return new UsernamePasswordCredentialsProvider(username, password == null ? "" : password);
    }

    private String appName(String namespace) {
        String value = "izykube-" + namespace;
        if (value.length() <= 63) {
            return value;
        }
        return value.substring(0, 63).replaceAll("-+$", "");
    }

    private String commitMessage(String clusterId, String namespace, boolean removeOnly) {
        if (removeOnly) {
            return "GitOps undeploy cluster " + clusterId + " namespace " + namespace;
        }
        return "GitOps deploy cluster " + clusterId + " namespace " + namespace;
    }

    private String argoRepoUrl() {
        if (StringUtils.hasText(gitOpsProperties.getArgoCdRepoUrl())) {
            return gitOpsProperties.getArgoCdRepoUrl();
        }
        return gitOpsProperties.getRepoUrl();
    }

    private void cleanYamlFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".yaml") || path.getFileName().toString().endsWith(".yml"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private void deletePathIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}

