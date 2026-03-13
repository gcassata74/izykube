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

import com.izylife.izykube.collections.ClusterStatusEnum;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.DeploymentWorkloadType;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.PodDTO;
import com.izylife.izykube.factory.ClientFactory;
import com.izylife.izykube.factory.NodeFactory;
import com.izylife.izykube.factory.TemplateFactory;
import com.izylife.izykube.model.Cluster;
import com.izylife.izykube.model.ClusterTemplate;
import com.izylife.izykube.model.ClusterVersion;
import com.izylife.izykube.repositories.ClusterTemplateRepository;
import com.izylife.izykube.repositories.ClusterRepository;
import com.izylife.izykube.repositories.ClusterVersionRepository;
import com.izylife.izykube.services.ai.ClusterYamlService;
import com.izylife.izykube.utils.ClusterUtil;
import io.fabric8.istio.api.networking.v1beta1.Gateway;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import io.fabric8.istio.client.IstioClient;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Resource;
import javassist.tools.rmi.ObjectNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class ClusterService {

    private static final int MAX_NAMESPACE_LENGTH = 63;
    private static final Set<String> CLUSTER_SCOPED_KINDS = Set.of(
            "namespace",
            "customresourcedefinition",
            "clusterrole",
            "clusterrolebinding",
            "storageclass",
            "persistentvolume",
            "mutatingwebhookconfiguration",
            "validatingwebhookconfiguration",
            "apiservice",
            "node"
    );

    private final ClientFactory clientFactory;
    private final ClusterRepository clusterRepository;
    private final ClusterTemplateRepository clusterTemplateRepository;
    private final ClusterVersionRepository clusterVersionRepository;
    private final TemplateFactory templateFactory;
    private final TemplateService templateService;
    private final ClusterYamlService clusterYamlService;
    private final NamespaceService namespaceService;


    public ClusterDTO createCluster(ClusterDTO clusterDTO) {

        try {
            SanitizedCluster sanitized = sanitizeClusterData(clusterDTO.getNodes(), clusterDTO.getLinks());

            clusterDTO.setNodes(sanitized.nodes());
            clusterDTO.setLinks(sanitized.links());
            String namespace = generateUniqueNamespace(clusterDTO.getName(), null);
            clusterDTO.setNameSpace(namespace);

            Cluster cluster = new Cluster();
            cluster.setName(clusterDTO.getName());
            cluster.setNameSpace(namespace);
            cluster.setNodes(sanitized.nodes());
            cluster.setLinks(sanitized.links());
            cluster.setDiagram(resolveDiagram(clusterDTO));
            cluster.setStatus(ClusterStatusEnum.INITIALIZED);
            Cluster savedCluster = clusterRepository.save(cluster);
            namespaceService.ensureNamespaceExists(namespace);
            saveClusterVersionSnapshot(savedCluster);

            return ClusterDTO.builder()
                    .id(savedCluster.getId())
                    .name(savedCluster.getName())
                    .nameSpace(savedCluster.getNameSpace())
                    .nodes(savedCluster.getNodes())
                    .links(savedCluster.getLinks())
                    .diagram(savedCluster.getDiagram())
                    .build();

        } catch (Exception e) {
            log.error("Error saving cluster: " + e.getMessage());
            return null;
        }
    }

    public ClusterDTO updateCluster(ClusterDTO clusterDTO) throws Exception {
        try {
            SanitizedCluster sanitized = sanitizeClusterData(clusterDTO.getNodes(), clusterDTO.getLinks());

            clusterDTO.setNodes(sanitized.nodes());
            clusterDTO.setLinks(sanitized.links());

            Cluster cluster = clusterRepository.findById(clusterDTO.getId()).orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));
            cluster.setId(clusterDTO.getId());
            cluster.setName(clusterDTO.getName());
            String namespace = resolveNamespaceForUpdate(cluster, clusterDTO);
            clusterDTO.setNameSpace(namespace);
            cluster.setNameSpace(namespace);
            cluster.setNodes(sanitized.nodes());
            cluster.setLinks(sanitized.links());
            cluster.setDiagram(resolveDiagram(clusterDTO));
            cluster.setStatus(ClusterStatusEnum.CREATED);
            Cluster updatedCluster = clusterRepository.save(cluster);
            namespaceService.ensureNamespaceExists(namespace);
            saveClusterVersionSnapshot(updatedCluster);

            return ClusterDTO.builder()
                    .id(updatedCluster.getId())
                    .name(updatedCluster.getName())
                    .nameSpace(updatedCluster.getNameSpace())
                    .nodes(updatedCluster.getNodes())
                    .links(updatedCluster.getLinks())
                    .diagram(updatedCluster.getDiagram())
                    .build();

        } catch (Exception e) {
            log.error("Error updating cluster", e);
            throw e;
        }
    }

    public Object getAllClusters() throws Exception {
        try {
            return clusterRepository.findAll();
        } catch (Exception e) {
            log.error("Error getting all clusters: " + e.getMessage());
            return null;
        }
    }

    public void deleteCluster(String id) {
        try {
            Cluster cluster = clusterRepository.findById(id).orElse(null);
            if (cluster == null) {
                return;
            }

            String namespace = cluster.getNameSpace();
            boolean namespaceUsedByAnotherCluster = namespace != null
                    && !namespace.isBlank()
                    && clusterRepository.isNamespaceInUse(namespace, id);

            clusterTemplateRepository.deleteByClusterId(id);
            clusterVersionRepository.deleteByClusterId(id);
            clusterRepository.deleteById(id);

            if (!namespaceUsedByAnotherCluster) {
                boolean namespaceDeleted = deleteNamespace(namespace);
                if (!namespaceDeleted) {
                    log.warn("Namespace {} was not fully deleted from cluster during cluster removal", namespace);
                }
                namespaceService.deleteNamespaceRecord(namespace);
            }
        } catch (Exception e) {
            log.error("Error deleting cluster: " + e.getMessage());
        }
    }

    public ClusterDTO getClusterById(String id) {

        try {
            Cluster cluster = clusterRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));
            ensureDiagram(cluster);

            SanitizedCluster sanitized = sanitizeClusterData(cluster.getNodes(), cluster.getLinks());

            // Use the factory to create appropriate NodeDTOs
            List<NodeDTO> nodeDTOs = sanitized.nodes().stream()
                    .map(NodeFactory::createNodeDTO)
                    .collect(Collectors.toList());
            List<LinkDTO> sanitizedLinks = sanitized.links();

            ClusterDTO clusterDTO = ClusterDTO.builder()
                    .id(cluster.getId())
                    .name(cluster.getName())
                    .nameSpace(cluster.getNameSpace())
                    .nodes(nodeDTOs)
                    .links(sanitizedLinks)
                    .diagram(cluster.getDiagram())
                    .status(cluster.getStatus())
                    .build();

            return clusterDTO;

        } catch (Exception e) {
            log.error("Error getting cluster with ID " + id + ": " + e.getMessage());
            return null;
        }

    }


    public void deploy(String clusterId) throws ObjectNotFoundException {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));

        String namespace = resolveClusterNamespace(cluster);

        ClusterTemplate template = clusterTemplateRepository.findByClusterId(clusterId)
                .orElseThrow(() -> new ObjectNotFoundException("Template not found for cluster ID: " + clusterId));

        ensureNamespaceMaterialized(namespace);

        for (String yaml : template.getYamlList()) {
            try {
                KubernetesClient loaderClient = (KubernetesClient) clientFactory.getClient("kubernetes");
                List<HasMetadata> resources = loaderClient
                        .load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)))
                        .items();

                if (resources == null || resources.isEmpty()) {
                    continue;
                }

                for (HasMetadata resource : resources) {
                    if (resource == null) {
                        continue;
                    }
                    applyNamespaceMetadata(resource, namespace);
                    Object client = clientFactory.getClient(resource.getApiVersion());

                    if (client instanceof IstioClient istioClient) {
                        deployIstioResource(istioClient, resource, namespace);
                    } else if (client instanceof KubernetesClient kubernetesClient) {
                        kubernetesClient.resource(resource).createOrReplace();
                    }

                    log.info("Deployed resource: " + resource.getKind() + "/" + resource.getMetadata().getName());
                }
            } catch (KubernetesClientException e) {
                log.error("Error deploying resource from template: " + e.getMessage());
            }
        }

        cluster.setStatus(ClusterStatusEnum.DEPLOYED);
        clusterRepository.save(cluster);
    }

    private void deployIstioResource(IstioClient istioClient, HasMetadata resource, String namespace) {
        String targetNamespace = (namespace == null || namespace.isBlank()) ? "default" : namespace;
        if (resource instanceof Gateway) {
            istioClient.v1beta1().gateways().inNamespace(targetNamespace).resource((Gateway) resource).create();
        } else if (resource instanceof VirtualService) {
            istioClient.v1beta1().virtualServices().inNamespace(targetNamespace).resource((VirtualService) resource).create();
        } else {
            log.warn("Unsupported Istio resource type: " + resource.getKind());
        }
    }

    private void applyTemplate(ClusterTemplate template, String namespace) {
        for (String yaml : template.getYamlList()) {
            try {
                // Load the YAML into Kubernetes resources
                KubernetesClient k8sClient = (KubernetesClient) clientFactory.getClient("kubernetes");
                List<HasMetadata> resources = k8sClient.load(new ByteArrayInputStream(yaml.getBytes())).items();

                // Create or update each resource
                for (HasMetadata resource : resources) {
                    applyNamespaceMetadata(resource, namespace);
                    k8sClient.resource(resource).createOrReplace();
                    log.info("Deployed resource: " + resource.getKind() + "/" + resource.getMetadata().getName());
                }
            } catch (KubernetesClientException e) {
                log.error("Error deploying resource from template: " + e.getMessage());
            }
        }
    }

    public void undeploy(String clusterId) throws ObjectNotFoundException {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));

        String namespace = resolveClusterNamespace(cluster);

        ClusterTemplate template = clusterTemplateRepository.findByClusterId(clusterId)
                .orElseThrow(() -> new ObjectNotFoundException("Template not found for cluster ID: " + clusterId));

        deleteResourcesFromTemplate(template, namespace);

        cluster.setStatus(ClusterStatusEnum.READY_FOR_DEPLOYMENT);
        clusterRepository.save(cluster);

        boolean namespaceDeleted = deleteNamespace(namespace);
        if (!namespaceDeleted) {
            log.warn("Namespace {} could not be fully removed. Please check cluster resources manually.", namespace);
        }
    }

    private void undeployIstioResource(IstioClient istioClient, HasMetadata resource, String namespace) {
        String targetNamespace = (namespace == null || namespace.isBlank()) ? "default" : namespace;
        if (resource instanceof Gateway) {
            istioClient.v1beta1().gateways().inNamespace(targetNamespace).resource((Gateway) resource).delete();
        } else if (resource instanceof VirtualService) {
            istioClient.v1beta1().virtualServices().inNamespace(targetNamespace).resource((VirtualService) resource).delete();
        } else {
            log.warn("Unsupported Istio resource type: " + resource.getKind());
        }
    }


    public Cluster patchCluster(String id, ClusterDTO clusterDTO) {
        try {
            // Find the existing cluster
            Cluster existingCluster = clusterRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Cluster not found with id: " + id));

            String namespace = resolveNamespaceForUpdate(existingCluster, clusterDTO);
            clusterDTO.setNameSpace(namespace);

            // Generate and save the template
            ClusterTemplate template = templateService.createOrReplaceTemplate(id, clusterDTO);
            if (template == null) {
                throw new IllegalStateException("Failed to generate template for cluster: " + id);
            }

            SanitizedCluster sanitized = sanitizeClusterData(clusterDTO.getNodes(), clusterDTO.getLinks());
            clusterDTO.setNodes(sanitized.nodes());
            clusterDTO.setLinks(sanitized.links());

            ensureNamespaceMaterialized(namespace);
            applyTemplate(template, namespace);

            // Update the cluster entity with new data
            existingCluster.setName(clusterDTO.getName());
            existingCluster.setNameSpace(namespace);
            existingCluster.setNodes(sanitized.nodes());
            existingCluster.setLinks(sanitized.links());
            existingCluster.setDiagram(resolveDiagram(clusterDTO));
            //keep the status as it is
            existingCluster.setStatus(existingCluster.getStatus());
            // Save the updated cluster
            Cluster updatedCluster = clusterRepository.save(existingCluster);
            namespaceService.ensureNamespaceExists(namespace);
            saveClusterVersionSnapshot(updatedCluster);
            return updatedCluster;


        } catch (ObjectNotFoundException e) {
            throw new RuntimeException("Failed to patch cluster: " + id, e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error while patching cluster: " + id, e);
        }
    }

    private SanitizedCluster sanitizeClusterData(List<NodeDTO> nodes, List<LinkDTO> links) {
        List<NodeDTO> inputNodes = nodes != null ? nodes : List.of();
        List<LinkDTO> inputLinks = links != null ? links : List.of();

        Map<String, NodeDTO> sanitizedNodeMap = new LinkedHashMap<>();
        Map<String, String> podReplacement = new HashMap<>();

        // Keep non-pod nodes
        for (NodeDTO node : inputNodes) {
            if (node == null) {
                continue;
            }
            if (!"pod".equalsIgnoreCase(node.getKind())) {
                sanitizedNodeMap.put(node.getId(), node);
            }
        }

        // Convert or map pod nodes
        for (NodeDTO node : inputNodes) {
            if (!(node instanceof PodDTO pod)) {
                continue;
            }

            String linkedDeploymentId = findLinkedDeploymentId(pod.getId(), inputLinks, sanitizedNodeMap);
            if (linkedDeploymentId != null) {
                podReplacement.put(pod.getId(), linkedDeploymentId);
                continue;
            }

            String newId = pod.getId();
            if (newId == null || newId.isBlank()) {
                newId = "deployment-" + System.nanoTime();
            }
            while (sanitizedNodeMap.containsKey(newId)) {
                newId = newId + "-dep";
            }

            DeploymentDTO replacement = convertPodToDeployment(pod, newId);
            sanitizedNodeMap.put(replacement.getId(), replacement);
            podReplacement.put(pod.getId(), replacement.getId());
        }

        List<LinkDTO> sanitizedLinks = new ArrayList<>();
        for (LinkDTO link : inputLinks) {
            if (link == null) {
                continue;
            }
            String source = podReplacement.getOrDefault(link.getSource(), link.getSource());
            String target = podReplacement.getOrDefault(link.getTarget(), link.getTarget());
            if (!sanitizedNodeMap.containsKey(source) || !sanitizedNodeMap.containsKey(target)) {
                continue;
            }
            LinkDTO sanitizedLink = new LinkDTO();
            sanitizedLink.setId(link.getId());
            sanitizedLink.setSource(source);
            sanitizedLink.setTarget(target);
            sanitizedLink.setType(normalizeLinkType(link.getType()));
            sanitizedLink.setNote(link.getNote());
            sanitizedLink.setContainerRole(link.getContainerRole());
            sanitizedLinks.add(sanitizedLink);
        }

        return new SanitizedCluster(new ArrayList<>(sanitizedNodeMap.values()), sanitizedLinks);
    }

    private String findLinkedDeploymentId(String podId, List<LinkDTO> links, Map<String, NodeDTO> nodeLookup) {
        if (podId == null || links == null) {
            return null;
        }
        for (LinkDTO link : links) {
            if (link == null) {
                continue;
            }
            if (podId.equals(link.getTarget())) {
                NodeDTO sourceNode = nodeLookup.get(link.getSource());
                if (sourceNode != null && "deployment".equalsIgnoreCase(sourceNode.getKind())) {
                    return sourceNode.getId();
                }
            }
        }
        return null;
    }

    private DeploymentDTO convertPodToDeployment(PodDTO pod, String deploymentId) {
        String name = pod.getName() != null ? pod.getName() : deploymentId;
        return new DeploymentDTO(deploymentId, name, 1, "RollingUpdate", "", 80, DeploymentWorkloadType.DEPLOYMENT);
    }

    private record SanitizedCluster(List<NodeDTO> nodes, List<LinkDTO> links) {}

    private String normalizeLinkType(String type) {
        if (type == null) {
            return "Expose";
        }
        String normalized = type.trim();
        if ("Use".equalsIgnoreCase(normalized)) {
            return "Use";
        }
        if ("Container".equalsIgnoreCase(normalized)) {
            return "Container";
        }
        if ("serviceAccountBinding".equalsIgnoreCase(normalized)) {
            return "serviceAccountBinding";
        }
        return "Expose";
    }

    private String resolveDiagram(ClusterDTO clusterDTO) {
        if (clusterDTO == null) {
            return "";
        }
        if (clusterDTO.getDiagram() != null && !clusterDTO.getDiagram().isBlank()) {
            return clusterDTO.getDiagram();
        }
        List<NodeDTO> nodes = clusterDTO.getNodes() != null ? clusterDTO.getNodes() : List.of();
        List<LinkDTO> links = clusterDTO.getLinks() != null ? clusterDTO.getLinks() : List.of();
        try {
            return clusterYamlService.buildDiagramSnapshot(nodes, links, List.of());
        } catch (Exception ex) {
            log.warn("Failed to build diagram snapshot for cluster {}: {}", clusterDTO.getName(), ex.getMessage());
            return "";
        }
    }

    private void ensureDiagram(Cluster cluster) {
        if (cluster == null) {
            return;
        }
        if (cluster.getDiagram() != null && !cluster.getDiagram().isBlank()) {
            return;
        }
        List<NodeDTO> nodes = cluster.getNodes() != null ? cluster.getNodes() : List.of();
        List<LinkDTO> links = cluster.getLinks() != null ? cluster.getLinks() : List.of();
        try {
            String diagram = clusterYamlService.buildDiagramSnapshot(nodes, links, List.of());
            cluster.setDiagram(diagram);
            clusterRepository.save(cluster);
        } catch (Exception ex) {
            log.warn("Failed to regenerate diagram for cluster {}: {}", cluster.getId(), ex.getMessage());
        }
    }

    private String resolveClusterNamespace(Cluster cluster) {
        if (cluster == null) {
            return "default";
        }
        String namespace = cluster.getNameSpace();
        if (namespace == null || namespace.isBlank()) {
            namespace = generateUniqueNamespace(cluster.getName(), cluster.getId());
            cluster.setNameSpace(namespace);
            clusterRepository.save(cluster);
        }
        namespaceService.ensureNamespaceExists(namespace);
        return namespace;
    }

    private void ensureNamespaceMaterialized(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return;
        }
        KubernetesClient k8sClient = (KubernetesClient) clientFactory.getClient("kubernetes");
        if (k8sClient.namespaces().withName(namespace).get() == null) {
            k8sClient.namespaces()
                    .resource(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build())
                    .create();
        }
    }

    private void deleteResourcesFromTemplate(ClusterTemplate template, String namespace) {
        if (template == null || template.getYamlList() == null) {
            return;
        }
        for (String yaml : template.getYamlList()) {
            try {
                KubernetesClient k8sClient = (KubernetesClient) clientFactory.getClient("kubernetes");
                List<HasMetadata> resources = k8sClient.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).get();

                if (resources == null || resources.isEmpty()) {
                    log.warn("No resources found in YAML during undeploy");
                    continue;
                }

                for (HasMetadata resource : resources) {
                    if (resource == null) {
                        continue;
                    }

                    applyNamespaceMetadata(resource, namespace);
                    Object client = clientFactory.getClient(resource.getApiVersion());

                    if (client instanceof IstioClient) {
                        undeployIstioResource((IstioClient) client, resource, namespace);
                    } else if (client instanceof KubernetesClient) {
                        ((KubernetesClient) client).resource(resource).delete();
                    }
                }

            } catch (KubernetesClientException e) {
                log.warn("Error undeploying resource from template: {}", e.getMessage());
            }
        }
    }

    private boolean deleteNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return true;
        }
        if ("default".equalsIgnoreCase(namespace)) {
            return true;
        }
        KubernetesClient k8sClient = (KubernetesClient) clientFactory.getClient("kubernetes");
        try {
            var op = k8sClient.namespaces().withName(namespace);
            List<io.fabric8.kubernetes.api.model.StatusDetails> result = op.delete();
            if (result == null || result.isEmpty()) {
                log.warn("Delete request for namespace {} was not acknowledged", namespace);
                return false;
            }
            boolean terminated = waitForNamespaceTermination(op, 30);
            if (terminated) {
                log.info("Namespace {} deleted from cluster", namespace);
            } else {
                log.warn("Namespace {} deletion is taking longer than expected", namespace);
            }
            return terminated;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Namespace deletion wait interrupted for {}: {}", namespace, ex.getMessage());
            return false;
        } catch (Exception ex) {
            log.warn("Unable to delete namespace {}: {}", namespace, ex.getMessage());
            return false;
        }
    }

    private boolean waitForNamespaceTermination(Resource<io.fabric8.kubernetes.api.model.Namespace> op, int timeoutSeconds) throws InterruptedException {
        int attempts = timeoutSeconds;
        while (attempts-- > 0) {
            if (op.get() == null) {
                return true;
            }
            TimeUnit.SECONDS.sleep(1);
        }
        return op.get() == null;
    }

    private void applyNamespaceMetadata(HasMetadata resource, String namespace) {
        if (resource == null || namespace == null || namespace.isBlank()) {
            return;
        }
        if (isClusterScopedKind(resource.getKind())) {
            return;
        }
        ObjectMeta metadata = resource.getMetadata();
        if (metadata == null) {
            metadata = new ObjectMeta();
            resource.setMetadata(metadata);
        }
        metadata.setNamespace(namespace);
    }

    private String resolveNamespaceForUpdate(Cluster existingCluster, ClusterDTO incomingCluster) {
        if (existingCluster != null && existingCluster.getNameSpace() != null && !existingCluster.getNameSpace().isBlank()) {
            return existingCluster.getNameSpace();
        }
        String dtoNamespace = incomingCluster != null ? incomingCluster.getNameSpace() : null;
        if (dtoNamespace != null && !dtoNamespace.isBlank()) {
            return sanitizeNamespace(dtoNamespace);
        }
        String dtoName = incomingCluster != null ? incomingCluster.getName() : null;
        String fallbackName = (dtoName == null || dtoName.isBlank())
                ? (existingCluster != null ? existingCluster.getName() : null)
                : dtoName;
        String currentClusterId = existingCluster != null ? existingCluster.getId() : null;
        return generateUniqueNamespace(fallbackName, currentClusterId);
    }

    private void saveClusterVersionSnapshot(Cluster cluster) {
        if (cluster == null || cluster.getId() == null) {
            return;
        }

        String namespace = cluster.getNameSpace();
        if (namespace == null || namespace.isBlank()) {
            namespace = sanitizeNamespace(cluster.getName());
        }

        int nextVersion = clusterVersionRepository
                .findFirstByNamespaceIgnoreCaseOrderByVersionNumberDesc(namespace)
                .map(version -> version.getVersionNumber() + 1)
                .orElse(1);

        ClusterVersion version = new ClusterVersion();
        version.setClusterId(cluster.getId());
        version.setClusterName(cluster.getName());
        version.setNamespace(namespace);
        version.setVersionNumber(nextVersion);
        version.setDiagram(cluster.getDiagram());
        version.setNodes(cluster.getNodes());
        version.setLinks(cluster.getLinks());
        version.setStatus(cluster.getStatus());
        version.setCreatedAt(Instant.now());
        clusterVersionRepository.save(version);
    }

    public List<ClusterVersion> getNamespaceVersions(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return List.of();
        }
        return clusterVersionRepository.findByNamespaceIgnoreCaseOrderByVersionNumberDesc(namespace);
    }

    public ClusterVersion getNamespaceVersion(String namespace, int versionNumber) throws ObjectNotFoundException {
        return clusterVersionRepository.findByNamespaceIgnoreCaseAndVersionNumber(namespace, versionNumber)
                .orElseThrow(() -> new ObjectNotFoundException("Version not found"));
    }

    public ClusterVersion getLatestNamespaceVersion(String namespace) throws ObjectNotFoundException {
        return clusterVersionRepository.findFirstByNamespaceIgnoreCaseOrderByVersionNumberDesc(namespace)
                .orElseThrow(() -> new ObjectNotFoundException("Version not found"));
    }

    public void deleteNamespaceVersion(String namespace, int versionNumber) throws ObjectNotFoundException {
        ClusterVersion version = clusterVersionRepository
                .findByNamespaceIgnoreCaseAndVersionNumber(namespace, versionNumber)
                .orElseThrow(() -> new ObjectNotFoundException("Version not found"));
        clusterVersionRepository.delete(version);
    }

    public void deleteNamespaceVersionById(String versionId) throws ObjectNotFoundException {
        if (versionId == null || versionId.isBlank()) {
            throw new ObjectNotFoundException("Version not found");
        }
        if (!clusterVersionRepository.existsById(versionId)) {
            throw new ObjectNotFoundException("Version not found");
        }
        clusterVersionRepository.deleteById(versionId);
    }

    private boolean isClusterScopedKind(String kind) {
        if (kind == null) {
            return false;
        }
        return CLUSTER_SCOPED_KINDS.contains(kind.toLowerCase(Locale.ROOT));
    }

    private String generateUniqueNamespace(String diagramName, String currentClusterId) {
        String baseName = sanitizeNamespace(diagramName);
        String candidate = baseName;
        int counter = 1;

        while (clusterRepository.isNamespaceInUse(candidate, currentClusterId)) {
            String suffix = "-" + counter++;
            int availableLength = Math.max(1, MAX_NAMESPACE_LENGTH - suffix.length());
            String trimmedBase = baseName.length() > availableLength ? baseName.substring(0, availableLength) : baseName;
            trimmedBase = trimmedBase.replaceAll("-+$", "");
            if (trimmedBase.isBlank()) {
                trimmedBase = "diagram";
            }
            if (trimmedBase.length() > availableLength) {
                trimmedBase = trimmedBase.substring(0, availableLength).replaceAll("-+$", "");
            }
            candidate = trimmedBase + suffix;
        }

        return candidate;
    }

    private String sanitizeNamespace(String value) {
        if (value == null || value.isBlank()) {
            return "diagram";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        normalized = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");

        if (normalized.isBlank()) {
            normalized = "diagram";
        }

        if (normalized.length() > MAX_NAMESPACE_LENGTH) {
            normalized = normalized.substring(0, MAX_NAMESPACE_LENGTH).replaceAll("-+$", "");
        }

        return normalized.isBlank() ? "diagram" : normalized;
    }
}
