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

import com.izylife.izykube.dto.operator.ManagedCrdRefDTO;
import com.izylife.izykube.dto.operator.OperatorCatalogActionRequestDTO;
import com.izylife.izykube.dto.operator.OperatorCatalogRequestDTO;
import com.izylife.izykube.dto.operator.OperatorCatalogResponseDTO;
import com.izylife.izykube.model.operator.ManagedCrdRef;
import com.izylife.izykube.model.operator.OperatorCatalogEntry;
import com.izylife.izykube.model.operator.OperatorInstallStatus;
import com.izylife.izykube.model.operator.OperatorUninstallPolicy;
import com.izylife.izykube.repositories.OperatorCatalogRepository;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperatorCatalogService {
    private static final Pattern CSV_VERSION_SUFFIX = Pattern.compile("(?i)\\.v\\d.*$");
    private static final String OWNERSHIP_MANAGED_BY_KEY = "izykube.io/managed-by";
    private static final String OWNERSHIP_MANAGED_BY_VALUE = "izykube";
    private static final String OWNERSHIP_CATALOG_ID_KEY = "izykube.io/operator-catalog-id";
    private static final String OWNERSHIP_PACKAGE_KEY = "izykube.io/operator-package";
    private static final Set<String> ALLOWED_OLM_SEED_KINDS = Set.of("Subscription", "OperatorGroup", "CatalogSource");
    private static final Set<String> BLOCKED_INSTALL_KINDS = Set.of("ClusterServiceVersion", "InstallPlan");
    private static final Duration FORCE_CSV_DELETE_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration FORCE_CSV_DELETE_SLEEP = Duration.ofMillis(800);

    private static final Set<String> CLUSTER_SCOPED_KINDS = Set.of(
            "Namespace",
            "Node",
            "PersistentVolume",
            "StorageClass",
            "PriorityClass",
            "CustomResourceDefinition",
            "ClusterRole",
            "ClusterRoleBinding",
            "MutatingWebhookConfiguration",
            "ValidatingWebhookConfiguration",
            "APIService",
            "RuntimeClass",
            "VolumeSnapshotClass"
    );

    private final OperatorCatalogRepository repository;
    private final KubernetesClient kubernetesClient;

    public List<OperatorCatalogResponseDTO> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(
                        (OperatorCatalogEntry entry) -> safeLower(entry.getName()),
                        Comparator.nullsLast(String::compareTo)
                ))
                .map(this::toDto)
                .toList();
    }

    public OperatorCatalogResponseDTO get(String id) {
        return toDto(getEntity(id));
    }

    public OperatorCatalogResponseDTO create(OperatorCatalogRequestDTO request) {
        OperatorCatalogEntry entry = new OperatorCatalogEntry();
        applyRequest(entry, request);
        validate(entry);
        return toDto(repository.save(entry));
    }

    public OperatorCatalogResponseDTO update(String id, OperatorCatalogRequestDTO request) {
        OperatorCatalogEntry entry = getEntity(id);
        applyRequest(entry, request);
        validate(entry);
        return toDto(repository.save(entry));
    }

    public void delete(String id) {
        OperatorCatalogEntry entry = getEntity(id);
        if (entry.getStatus() != OperatorInstallStatus.NOT_INSTALLED) {
            throw new IllegalStateException("Operator must be uninstalled before deleting from catalog");
        }
        repository.deleteById(id);
    }

    public OperatorCatalogResponseDTO install(String id, OperatorCatalogActionRequestDTO request) {
        OperatorCatalogEntry entry = getEntity(id);
        if (request != null && StringUtils.hasText(request.getTargetVersion())) {
            entry.setDesiredVersion(request.getTargetVersion().trim());
        }
        validateForInstall(entry);

        entry.setStatus(OperatorInstallStatus.INSTALLING);
        entry.setLastMessage("Install in progress");
        entry.setLastActionAt(LocalDateTime.now());
        repository.save(entry);

        try {
            List<HasMetadata> resources = sanitizeInstallResources(loadResources(entry.getManifestYaml()), entry);
            if (CollectionUtils.isEmpty(resources)) {
                throw new IllegalArgumentException("manifestYaml must contain at least one Kubernetes resource");
            }
            applyResources(resources, entry.getTargetNamespace());

            entry.setInstalledVersion(entry.getDesiredVersion());
            entry.setStatus(OperatorInstallStatus.INSTALLED);
            entry.setLastMessage("Installed successfully");
            entry.setLastActionAt(LocalDateTime.now());
            return toDto(repository.save(entry));
        } catch (Exception e) {
            log.error("Error installing operator {}: {}", id, e.getMessage(), e);
            entry.setStatus(OperatorInstallStatus.DEGRADED);
            entry.setLastMessage("Install failed: " + e.getMessage());
            entry.setLastActionAt(LocalDateTime.now());
            repository.save(entry);
            throw new IllegalStateException("Install failed: " + e.getMessage(), e);
        }
    }

    public OperatorCatalogResponseDTO upgrade(String id, OperatorCatalogActionRequestDTO request) {
        OperatorCatalogEntry entry = getEntity(id);
        if (request != null && StringUtils.hasText(request.getTargetVersion())) {
            entry.setDesiredVersion(request.getTargetVersion().trim());
        }
        validateForInstall(entry);

        entry.setStatus(OperatorInstallStatus.UPGRADING);
        entry.setLastMessage("Upgrade in progress");
        entry.setLastActionAt(LocalDateTime.now());
        repository.save(entry);

        try {
            List<HasMetadata> resources = sanitizeInstallResources(loadResources(entry.getManifestYaml()), entry);
            if (CollectionUtils.isEmpty(resources)) {
                throw new IllegalArgumentException("manifestYaml must contain at least one Kubernetes resource");
            }
            applyResources(resources, entry.getTargetNamespace());

            entry.setInstalledVersion(entry.getDesiredVersion());
            entry.setStatus(OperatorInstallStatus.INSTALLED);
            entry.setLastMessage("Updated successfully");
            entry.setLastActionAt(LocalDateTime.now());
            return toDto(repository.save(entry));
        } catch (Exception e) {
            log.error("Error upgrading operator {}: {}", id, e.getMessage(), e);
            entry.setStatus(OperatorInstallStatus.DEGRADED);
            entry.setLastMessage("Update failed: " + e.getMessage());
            entry.setLastActionAt(LocalDateTime.now());
            repository.save(entry);
            throw new IllegalStateException("Update failed: " + e.getMessage(), e);
        }
    }

    public OperatorCatalogResponseDTO uninstall(String id, OperatorCatalogActionRequestDTO request) {
        OperatorCatalogEntry entry = getEntity(id);
        boolean force = request != null && Boolean.TRUE.equals(request.getForce());
        if (entry.getUninstallPolicy() == OperatorUninstallPolicy.FORCE_DELETE) {
            force = true;
        }

        entry.setStatus(OperatorInstallStatus.UNINSTALLING);
        entry.setLastMessage("Uninstall in progress");
        entry.setLastActionAt(LocalDateTime.now());
        repository.save(entry);

        try {
            Map<String, Integer> crUsage = countManagedCustomResources(entry);
            Map<String, Integer> blockers = crUsage.entrySet().stream()
                    .filter(item -> item.getValue() != null && item.getValue() > 0)
                    .collect(LinkedHashMap::new, (map, item) -> map.put(item.getKey(), item.getValue()), Map::putAll);

            if (!blockers.isEmpty() && !force) {
                String details = blockers.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("unknown");
                entry.setStatus(OperatorInstallStatus.DEGRADED);
                entry.setLastMessage("Uninstall blocked by custom resources: " + details);
                entry.setLastActionAt(LocalDateTime.now());
                repository.save(entry);
                throw new IllegalStateException("Uninstall blocked by custom resources: " + details + ". Use force to continue.");
            }

            List<HasMetadata> resources = safeLoadResources(entry.getManifestYaml());

            // 1) Remove OLM seed resources (Subscription/InstallPlan/CSV) first
            cleanupOlmResources(entry, resources);

            // 2) Force mode: aggressively remove matching CSVs (clear finalizers + delete + retry)
            if (force) {
                aggressivelyDeleteMatchingCsvs(entry, resources);
            }

            // 3) Remove resources explicitly present in manifest (Subscription/OperatorGroup/CatalogSource, etc.)
            deleteResources(resources, entry, blockers.isEmpty());

            // 4) Remove CRDs owned by CSVs of this operator (installed asynchronously by OLM)
            //    This covers the case where CRDs are not present in manifestYaml.
            Set<String> namespaces = collectOlmNamespaces(entry, resources);

            ResourceDefinitionContext csvContext = new ResourceDefinitionContext.Builder()
                    .withGroup("operators.coreos.com")
                    .withVersion("v1alpha1")
                    .withPlural("clusterserviceversions")
                    .withNamespaced(true)
                    .build();

            ResourceDefinitionContext crdContext = new ResourceDefinitionContext.Builder()
                    .withGroup("apiextensions.k8s.io")
                    .withVersion("v1")
                    .withPlural("customresourcedefinitions")
                    .build();

            String normalizedPackage = normalizePackageName(entry.getPackageName());
            String normalizedEntryName = normalizePackageName(entry.getName());
            Set<String> csvMatchCandidates = new LinkedHashSet<>();
            if (StringUtils.hasText(normalizedPackage)) {
                csvMatchCandidates.add(normalizedPackage);
            }
            if (StringUtils.hasText(normalizedEntryName)) {
                csvMatchCandidates.add(normalizedEntryName);
            }

            Set<String> candidateCsvNames = new LinkedHashSet<>();
            for (GenericKubernetesResource csv : safeList(csvContext, namespaces)) {
                ObjectMeta metadata = csv.getMetadata();
                if (metadata == null || !StringUtils.hasText(metadata.getName())) {
                    continue;
                }
                String csvName = metadata.getName();
                boolean owned = isOwnedResource(csv, entry);
                boolean pkgMatch = csvMatchesPackage(csvName, csvMatchCandidates);
                if (owned || pkgMatch) {
                    candidateCsvNames.add(csvName);
                }
            }

            Set<String> crdNamesFromCsv = new LinkedHashSet<>();
            for (GenericKubernetesResource csv : safeList(csvContext, namespaces)) {
                ObjectMeta metadata = csv.getMetadata();
                if (metadata == null || !StringUtils.hasText(metadata.getName())) {
                    continue;
                }
                if (!candidateCsvNames.contains(metadata.getName())) {
                    continue;
                }

                Map<?, ?> spec = readMap(csv.getAdditionalProperties().get("spec"));
                Map<?, ?> customresourcedefinitions = readMap(spec.get("customresourcedefinitions"));

                Object ownedObj = customresourcedefinitions.get("owned");
                if (ownedObj instanceof List<?> ownedList) {
                    for (Object item : ownedList) {
                        Map<?, ?> ownedMap = readMap(item);
                        String crdName = readString(ownedMap, "name");
                        if (StringUtils.hasText(crdName)) {
                            crdNamesFromCsv.add(crdName);
                        }
                    }
                }
            }

            if (!crdNamesFromCsv.isEmpty()) {
                for (GenericKubernetesResource crd : safeListAnyNamespace(crdContext)) {
                    ObjectMeta metadata = crd.getMetadata();
                    if (metadata == null || !StringUtils.hasText(metadata.getName())) {
                        continue;
                    }
                    if (!crdNamesFromCsv.contains(metadata.getName())) {
                        continue;
                    }
                    if (shouldRetainCrd(entry, blockers.isEmpty())) {
                        continue;
                    }
                    safeDeleteByName(crdContext, null, metadata.getName());
                }
            }

            entry.setInstalledVersion(null);
            entry.setStatus(OperatorInstallStatus.NOT_INSTALLED);
            entry.setLastMessage("Uninstalled successfully");
            entry.setLastActionAt(LocalDateTime.now());
            return toDto(repository.save(entry));
        } catch (Exception e) {
            log.error("Error uninstalling operator {}: {}", id, e.getMessage(), e);
            entry.setStatus(OperatorInstallStatus.DEGRADED);
            entry.setLastMessage("Uninstall failed: " + e.getMessage());
            entry.setLastActionAt(LocalDateTime.now());
            repository.save(entry);
            throw new IllegalStateException("Uninstall failed: " + e.getMessage(), e);
        }
    }

    private OperatorCatalogEntry getEntity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Catalog entry not found " + id));
    }

    private void applyRequest(OperatorCatalogEntry entry, OperatorCatalogRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        entry.setName(trimToNull(request.getName()));
        entry.setPackageName(trimToNull(request.getPackageName()));
        entry.setChannel(trimToNull(request.getChannel()));
        entry.setTargetNamespace(trimToNull(request.getTargetNamespace()));
        entry.setDesiredVersion(trimToNull(request.getDesiredVersion()));
        entry.setManifestYaml(request.getManifestYaml());
        entry.setUninstallPolicy(request.getUninstallPolicy() == null ? OperatorUninstallPolicy.RETAIN_CRDS : request.getUninstallPolicy());

        List<ManagedCrdRef> managedCrds = (request.getManagedCrds() == null ? List.<ManagedCrdRefDTO>of() : request.getManagedCrds())
                .stream()
                .filter(Objects::nonNull)
                .map(this::toEntity)
                .toList();
        entry.setManagedCrds(managedCrds);

        if (entry.getStatus() == null) {
            entry.setStatus(OperatorInstallStatus.NOT_INSTALLED);
        }
    }

    private ManagedCrdRef toEntity(ManagedCrdRefDTO dto) {
        ManagedCrdRef entity = new ManagedCrdRef();
        entity.setGroup(trimToNull(dto.getGroup()));
        entity.setVersion(trimToNull(dto.getVersion()));
        entity.setPlural(trimToNull(dto.getPlural()));
        entity.setNamespaced(dto.getNamespaced() == null ? Boolean.TRUE : dto.getNamespaced());
        return entity;
    }

    private ManagedCrdRefDTO toDto(ManagedCrdRef entity) {
        ManagedCrdRefDTO dto = new ManagedCrdRefDTO();
        dto.setGroup(entity.getGroup());
        dto.setVersion(entity.getVersion());
        dto.setPlural(entity.getPlural());
        dto.setNamespaced(entity.getNamespaced());
        return dto;
    }

    private OperatorCatalogResponseDTO toDto(OperatorCatalogEntry entry) {
        OperatorCatalogResponseDTO dto = new OperatorCatalogResponseDTO();
        dto.setId(entry.getId());
        dto.setName(entry.getName());
        dto.setPackageName(entry.getPackageName());
        dto.setChannel(entry.getChannel());
        dto.setTargetNamespace(entry.getTargetNamespace());
        dto.setDesiredVersion(entry.getDesiredVersion());
        dto.setInstalledVersion(entry.getInstalledVersion());
        dto.setUninstallPolicy(entry.getUninstallPolicy());
        dto.setStatus(entry.getStatus());
        dto.setLastMessage(entry.getLastMessage());
        dto.setManifestYaml(entry.getManifestYaml());
        dto.setManagedCrds(entry.getManagedCrds() == null ? List.of() : entry.getManagedCrds().stream().map(this::toDto).toList());
        dto.setUpdatedAt(entry.getLastUpdated() == null ? null : entry.getLastUpdated().atOffset(ZoneOffset.UTC).toString());
        dto.setLastActionAt(entry.getLastActionAt() == null ? null : entry.getLastActionAt().atOffset(ZoneOffset.UTC).toString());
        return dto;
    }

    private void validate(OperatorCatalogEntry entry) {
        if (!StringUtils.hasText(entry.getName())) {
            throw new IllegalArgumentException("name is required");
        }
        if (!StringUtils.hasText(entry.getPackageName())) {
            throw new IllegalArgumentException("packageName is required");
        }
        if (!StringUtils.hasText(entry.getTargetNamespace())) {
            throw new IllegalArgumentException("targetNamespace is required");
        }
        if (!StringUtils.hasText(entry.getDesiredVersion())) {
            throw new IllegalArgumentException("desiredVersion is required");
        }

        List<ManagedCrdRef> managedCrds = entry.getManagedCrds() == null ? List.of() : entry.getManagedCrds();
        for (ManagedCrdRef managedCrd : managedCrds) {
            if (managedCrd == null) {
                continue;
            }
            if (!StringUtils.hasText(managedCrd.getGroup())) {
                throw new IllegalArgumentException("managedCrds.group is required");
            }
            if (!StringUtils.hasText(managedCrd.getVersion())) {
                throw new IllegalArgumentException("managedCrds.version is required");
            }
            if (!StringUtils.hasText(managedCrd.getPlural())) {
                throw new IllegalArgumentException("managedCrds.plural is required");
            }
            if (managedCrd.getNamespaced() == null) {
                managedCrd.setNamespaced(Boolean.TRUE);
            }
        }
    }

    private void validateForInstall(OperatorCatalogEntry entry) {
        validate(entry);
        if (!StringUtils.hasText(entry.getManifestYaml())) {
            throw new IllegalArgumentException("manifestYaml is required for install/upgrade");
        }
    }

    private List<HasMetadata> loadResources(String yaml) {
        if (!StringUtils.hasText(yaml)) {
            return List.of();
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))) {
            return kubernetesClient.load(input).items();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid manifestYaml: " + e.getMessage(), e);
        }
    }

    private List<HasMetadata> safeLoadResources(String yaml) {
        try {
            return loadResources(yaml);
        } catch (Exception ex) {
            log.warn("Unable to parse manifestYaml during uninstall, continuing with OLM cleanup only: {}", ex.getMessage());
            return List.of();
        }
    }

    List<HasMetadata> sanitizeInstallResources(List<HasMetadata> resources, OperatorCatalogEntry entry) {
        if (resources == null) {
            return List.of();
        }
        List<HasMetadata> sanitized = new ArrayList<>();
        for (HasMetadata resource : resources) {
            if (resource == null || resource.getMetadata() == null || !StringUtils.hasText(resource.getKind())) {
                continue;
            }
            if (BLOCKED_INSTALL_KINDS.contains(resource.getKind())) {
                throw new IllegalArgumentException("manifestYaml cannot include " + resource.getKind() + ". OLM manages it automatically.");
            }
            if (!isAllowedOlmSeedResource(resource)) {
                throw new IllegalArgumentException("manifestYaml can only include OLM seed resources: Subscription, OperatorGroup, CatalogSource");
            }
            normalizeNamespaceForInstall(resource, entry.getTargetNamespace());
            applyOwnershipLabels(resource, entry);
            sanitized.add(resource);
        }
        return sanitized;
    }

    private void applyResources(List<HasMetadata> resources, String targetNamespace) {
        for (HasMetadata resource : resources) {
            if (resource == null || resource.getMetadata() == null || !StringUtils.hasText(resource.getKind())) {
                continue;
            }
            String namespace = resolveNamespace(resource, targetNamespace, false);
            if (StringUtils.hasText(namespace)) {
                ensureNamespaceExists(namespace);
            }
            applyResource(resource, namespace);
        }
    }

    private void deleteResources(List<HasMetadata> resources, OperatorCatalogEntry entry, boolean noCrsPresent) {
        List<HasMetadata> reversed = new ArrayList<>(resources);
        java.util.Collections.reverse(reversed);

        for (HasMetadata resource : reversed) {
            if (resource == null || resource.getMetadata() == null || !StringUtils.hasText(resource.getKind())) {
                continue;
            }

            if (isCrd(resource) && shouldRetainCrd(entry, noCrsPresent)) {
                continue;
            }

            String namespace = resolveNamespace(resource, entry.getTargetNamespace(), true);
            try {
                deleteResource(resource, namespace);
            } catch (Exception ex) {
                log.warn("Unable to delete resource {}/{} in namespace {}: {}",
                        resource.getApiVersion(),
                        resource.getKind(),
                        namespace,
                        ex.getMessage());
            }
        }
    }

    private void applyResource(HasMetadata resource, String namespace) {
        try {
            if (StringUtils.hasText(namespace)) {
                kubernetesClient.resource(resource).inNamespace(namespace).createOrReplace();
            } else {
                kubernetesClient.resource(resource).createOrReplace();
            }
        } catch (Exception ex) {
            if (!canUseGenericFallback(resource)) {
                throw ex;
            }
            applyGenericResource(resource, namespace);
        }
    }

    private void deleteResource(HasMetadata resource, String namespace) {
        try {
            if (StringUtils.hasText(namespace)) {
                kubernetesClient.resource(resource).inNamespace(namespace).delete();
            } else {
                kubernetesClient.resource(resource).delete();
            }
        } catch (Exception ex) {
            if (!canUseGenericFallback(resource)) {
                throw ex;
            }
            deleteGenericResource(resource, namespace);
        }
    }

    private boolean canUseGenericFallback(HasMetadata resource) {
        return StringUtils.hasText(resource.getApiVersion())
                && StringUtils.hasText(resource.getKind())
                && resource.getMetadata() != null
                && StringUtils.hasText(resource.getMetadata().getName());
    }

    private void applyGenericResource(HasMetadata resource, String namespace) {
        ResourceDefinitionContext context = buildContext(resource);
        GenericKubernetesResource genericResource = toGenericResource(resource);
        if (StringUtils.hasText(namespace)) {
            kubernetesClient.genericKubernetesResources(context)
                    .inNamespace(namespace)
                    .resource(genericResource)
                    .createOrReplace();
        } else {
            kubernetesClient.genericKubernetesResources(context)
                    .resource(genericResource)
                    .createOrReplace();
        }
    }

    private void deleteGenericResource(HasMetadata resource, String namespace) {
        ResourceDefinitionContext context = buildContext(resource);
        String name = resource.getMetadata().getName();
        if (StringUtils.hasText(namespace)) {
            kubernetesClient.genericKubernetesResources(context)
                    .inNamespace(namespace)
                    .withName(name)
                    .delete();
        } else {
            kubernetesClient.genericKubernetesResources(context)
                    .withName(name)
                    .delete();
        }
    }

    private ResourceDefinitionContext buildContext(HasMetadata resource) {
        String apiVersion = resource.getApiVersion();
        String group = "";
        String version = apiVersion;
        int slashIdx = apiVersion.indexOf('/');
        if (slashIdx > -1) {
            group = apiVersion.substring(0, slashIdx);
            version = apiVersion.substring(slashIdx + 1);
        }

        return new ResourceDefinitionContext.Builder()
                .withGroup(group)
                .withVersion(version)
                .withPlural(toPlural(resource.getKind()))
                .build();
    }

    private GenericKubernetesResource toGenericResource(HasMetadata resource) {
        if (resource instanceof GenericKubernetesResource genericKubernetesResource) {
            return genericKubernetesResource;
        }
        return Serialization.jsonMapper().convertValue(resource, GenericKubernetesResource.class);
    }

    private String toPlural(String kind) {
        String base = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(base)) {
            return base;
        }
        if (base.endsWith("s") || base.endsWith("x") || base.endsWith("z") || base.endsWith("ch") || base.endsWith("sh")) {
            return base + "es";
        }
        if (base.endsWith("y") && base.length() > 1) {
            char prev = base.charAt(base.length() - 2);
            boolean isVowel = prev == 'a' || prev == 'e' || prev == 'i' || prev == 'o' || prev == 'u';
            if (!isVowel) {
                return base.substring(0, base.length() - 1) + "ies";
            }
        }
        return base + "s";
    }

    private void ensureNamespaceExists(String namespace) {
        if (!StringUtils.hasText(namespace)) {
            return;
        }
        if (kubernetesClient.namespaces().withName(namespace).get() != null) {
            return;
        }
        kubernetesClient.namespaces()
                .resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withName(namespace)
                        .endMetadata()
                        .build())
                .create();
    }

    private void cleanupOlmResources(OperatorCatalogEntry entry, List<HasMetadata> resources) {
        Set<String> subscriptionNames = resources.stream()
                .filter(Objects::nonNull)
                .filter(item -> "Subscription".equalsIgnoreCase(item.getKind()))
                .map(HasMetadata::getMetadata)
                .filter(Objects::nonNull)
                .map(ObjectMeta::getName)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> namespaces = collectOlmNamespaces(entry, resources);

        String packageName = trimToNull(entry.getPackageName());
        String entryName = trimToNull(entry.getName());
        if (subscriptionNames.isEmpty() && !StringUtils.hasText(packageName) && !StringUtils.hasText(entryName)) {
            return;
        }

        ResourceDefinitionContext subscriptionsContext = new ResourceDefinitionContext.Builder()
                .withGroup("operators.coreos.com")
                .withVersion("v1alpha1")
                .withPlural("subscriptions")
                .withNamespaced(true)
                .build();
        ResourceDefinitionContext installPlansContext = new ResourceDefinitionContext.Builder()
                .withGroup("operators.coreos.com")
                .withVersion("v1alpha1")
                .withPlural("installplans")
                .withNamespaced(true)
                .build();
        ResourceDefinitionContext csvContext = new ResourceDefinitionContext.Builder()
                .withGroup("operators.coreos.com")
                .withVersion("v1alpha1")
                .withPlural("clusterserviceversions")
                .withNamespaced(true)
                .build();
        ResourceDefinitionContext operatorsContext = new ResourceDefinitionContext.Builder()
                .withGroup("operators.coreos.com")
                .withVersion("v1")
                .withPlural("operators")
                .withNamespaced(true)
                .build();

        List<GenericKubernetesResource> subscriptions = safeList(subscriptionsContext, namespaces);

        Set<String> removedCsvNames = new HashSet<>();
        Set<String> packageNames = new HashSet<>();
        String normalizedPackageName = normalizePackageName(packageName);
        if (StringUtils.hasText(normalizedPackageName)) {
            packageNames.add(normalizedPackageName);
        }
        String normalizedEntryName = normalizePackageName(entryName);
        if (StringUtils.hasText(normalizedEntryName)) {
            packageNames.add(normalizedEntryName);
        }
        for (HasMetadata resource : resources) {
            if (resource == null || resource.getMetadata() == null || !StringUtils.hasText(resource.getKind())) {
                continue;
            }
            if ("ClusterServiceVersion".equalsIgnoreCase(resource.getKind())) {
                String csvName = trimToNull(resource.getMetadata().getName());
                if (StringUtils.hasText(csvName)) {
                    removedCsvNames.add(csvName);
                    String pkg = normalizePackageName(csvName);
                    if (StringUtils.hasText(pkg)) {
                        packageNames.add(pkg);
                    }
                }
            }
        }
        for (GenericKubernetesResource subscription : subscriptions) {
            ObjectMeta metadata = subscription.getMetadata();
            if (metadata == null || !StringUtils.hasText(metadata.getNamespace()) || !StringUtils.hasText(metadata.getName())) {
                continue;
            }
            if (!isOwnedResource(subscription, entry)
                    && !matchesSubscription(subscription, packageName, subscriptionNames)) {
                continue;
            }
            String subscriptionPackageName = readString(readMap(subscription.getAdditionalProperties().get("spec")), "name");
            if (StringUtils.hasText(subscriptionPackageName)) {
                packageNames.add(normalizePackageName(subscriptionPackageName));
            }

            String installedCsv = readString(readMap(subscription.getAdditionalProperties().get("status")), "installedCSV");
            if (StringUtils.hasText(installedCsv)) {
                removedCsvNames.add(installedCsv);
            }
            safeDeleteByName(subscriptionsContext, metadata.getNamespace(), metadata.getName());
        }

        for (GenericKubernetesResource csv : safeList(csvContext, namespaces)) {
            ObjectMeta metadata = csv.getMetadata();
            if (metadata == null || !StringUtils.hasText(metadata.getName())) {
                continue;
            }
            if (isOwnedResource(csv, entry)
                    || shouldDeleteCsv(metadata.getName(), removedCsvNames, packageNames)) {
                removedCsvNames.add(metadata.getName());
                safeDeleteByName(csvContext, metadata.getNamespace(), metadata.getName());
            }
        }

        for (GenericKubernetesResource installPlan : safeList(installPlansContext, namespaces)) {
            ObjectMeta metadata = installPlan.getMetadata();
            if (metadata == null || !StringUtils.hasText(metadata.getName())) {
                continue;
            }
            Map<?, ?> spec = readMap(installPlan.getAdditionalProperties().get("spec"));
            if (spec.isEmpty()) {
                continue;
            }
            boolean shouldDelete = false;
            Object csvNamesObj = spec.get("clusterServiceVersionNames");
            if (csvNamesObj instanceof List<?> csvNames) {
                shouldDelete = csvNames.stream()
                        .map(String::valueOf)
                        .anyMatch(removedCsvNames::contains);
            }
            if (!shouldDelete && !packageNames.isEmpty()) {
                String csvName = readString(spec, "clusterServiceVersionName");
                shouldDelete = csvMatchesPackage(csvName, packageNames);
            }
            if (!shouldDelete && isOwnedResource(installPlan, entry)) {
                shouldDelete = true;
            }
            if (shouldDelete) {
                safeDeleteByName(installPlansContext, metadata.getNamespace(), metadata.getName());
            }
        }

        for (GenericKubernetesResource operator : safeList(operatorsContext, namespaces)) {
            ObjectMeta metadata = operator.getMetadata();
            if (metadata == null || !StringUtils.hasText(metadata.getName())) {
                continue;
            }
            if (shouldDeleteOperatorResource(operator, entry, packageNames)) {
                safeDeleteByName(operatorsContext, metadata.getNamespace(), metadata.getName());
            }
        }
    }

    private boolean shouldDeleteOperatorResource(GenericKubernetesResource operator,
                                                 OperatorCatalogEntry entry,
                                                 Set<String> packageNames) {
        if (operator == null || operator.getMetadata() == null || !StringUtils.hasText(operator.getMetadata().getName())) {
            return false;
        }
        if (isOwnedResource(operator, entry)) {
            return true;
        }

        String name = operator.getMetadata().getName().toLowerCase(Locale.ROOT);
        boolean nameMatches = packageNames.stream()
                .filter(StringUtils::hasText)
                .anyMatch(pkg -> name.contains(pkg.toLowerCase(Locale.ROOT)));
        if (nameMatches) {
            return true;
        }

        Map<?, ?> spec = readMap(operator.getAdditionalProperties().get("spec"));
        String[] candidates = new String[] {
                readString(spec, "name"),
                readString(spec, "packageName"),
                readString(spec, "package")
        };
        for (String candidate : candidates) {
            String normalized = normalizePackageName(candidate);
            if (StringUtils.hasText(normalized) && packageNames.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private List<GenericKubernetesResource> safeList(ResourceDefinitionContext context, Set<String> namespaces) {
        try {
            return kubernetesClient.genericKubernetesResources(context)
                    .inAnyNamespace()
                    .list()
                    .getItems();
        } catch (Exception ex) {
            log.warn("Unable to list resources for {}/{} in all namespaces: {}. Falling back to scoped namespaces.",
                    context.getGroup(), context.getPlural(), ex.getMessage());
        }

        Set<String> candidateNamespaces = new LinkedHashSet<>(namespaces == null ? Set.of() : namespaces);
        try {
            kubernetesClient.namespaces().list().getItems().stream()
                    .map(Namespace::getMetadata)
                    .filter(Objects::nonNull)
                    .map(ObjectMeta::getName)
                    .filter(StringUtils::hasText)
                    .forEach(candidateNamespaces::add);
        } catch (Exception ex) {
            log.warn("Unable to enumerate namespaces for fallback listing of {}/{}: {}",
                    context.getGroup(), context.getPlural(), ex.getMessage());
        }

        List<GenericKubernetesResource> merged = new ArrayList<>();
        for (String namespace : candidateNamespaces) {
            if (!StringUtils.hasText(namespace)) {
                continue;
            }
            try {
                List<GenericKubernetesResource> items = kubernetesClient.genericKubernetesResources(context)
                        .inNamespace(namespace)
                        .list()
                        .getItems();
                merged.addAll(items);
            } catch (Exception ex) {
                log.warn("Unable to list resources for {}/{} in namespace {}: {}",
                        context.getGroup(), context.getPlural(), namespace, ex.getMessage());
            }
        }
        return merged;
    }

    private Set<String> collectOlmNamespaces(OperatorCatalogEntry entry, List<HasMetadata> resources) {
        Set<String> namespaces = new LinkedHashSet<>();
        namespaces.add(trimToNull(entry.getTargetNamespace()));
        namespaces.add("operators");
        namespaces.add("olm");

        for (HasMetadata resource : resources) {
            if (resource == null || resource.getMetadata() == null || !StringUtils.hasText(resource.getKind())) {
                continue;
            }
            String kind = resource.getKind();
            if ("Subscription".equalsIgnoreCase(kind)
                    || "ClusterServiceVersion".equalsIgnoreCase(kind)
                    || "InstallPlan".equalsIgnoreCase(kind)) {
                namespaces.add(trimToNull(resource.getMetadata().getNamespace()));
            }
        }
        return namespaces.stream().filter(StringUtils::hasText).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean shouldDeleteCsv(String csvName, Set<String> removedCsvNames, Set<String> packageNames) {
        if (!StringUtils.hasText(csvName)) {
            return false;
        }
        if (removedCsvNames.contains(csvName)) {
            return true;
        }
        return csvMatchesPackage(csvName, packageNames);
    }

    private boolean csvMatchesPackage(String csvName, Set<String> packageNames) {
        if (!StringUtils.hasText(csvName) || packageNames == null || packageNames.isEmpty()) {
            return false;
        }
        String normalizedCsvName = trimToNull(csvName);
        if (!StringUtils.hasText(normalizedCsvName)) {
            return false;
        }
        String normalizedCsvNameLower = normalizedCsvName.toLowerCase(Locale.ROOT);
        return packageNames.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizePackageName)
                .filter(StringUtils::hasText)
                .anyMatch(pkg -> normalizedCsvNameLower.equals(pkg) || normalizedCsvNameLower.startsWith(pkg + ".v"));
    }

    private List<GenericKubernetesResource> safeListAnyNamespace(ResourceDefinitionContext context) {
        try {
            return kubernetesClient.genericKubernetesResources(context)
                    .inAnyNamespace()
                    .list()
                    .getItems();
        } catch (Exception ex) {
            log.warn("Unable to list resources for {}/{}: {}", context.getGroup(), context.getPlural(), ex.getMessage());
            return List.of();
        }
    }

    private void safeDeleteByName(ResourceDefinitionContext context, String namespace, String name) {
        try {
            if (StringUtils.hasText(namespace)) {
                kubernetesClient.genericKubernetesResources(context)
                        .inNamespace(namespace)
                        .withName(name)
                        .delete();
            } else {
                kubernetesClient.genericKubernetesResources(context)
                        .withName(name)
                        .delete();
            }
        } catch (Exception ex) {
            log.warn("Unable to delete {} {}/{}: {}", context.getPlural(), namespace, name, ex.getMessage());
        }
    }

    private void safeClearFinalizersByName(ResourceDefinitionContext context, String namespace, String name) {
        try {
            if (StringUtils.hasText(namespace)) {
                kubernetesClient.genericKubernetesResources(context)
                        .inNamespace(namespace)
                        .withName(name)
                        .edit(resource -> {
                            if (resource != null && resource.getMetadata() != null) {
                                resource.getMetadata().setFinalizers(new ArrayList<>());
                            }
                            return resource;
                        });
            } else {
                kubernetesClient.genericKubernetesResources(context)
                        .withName(name)
                        .edit(resource -> {
                            if (resource != null && resource.getMetadata() != null) {
                                resource.getMetadata().setFinalizers(new ArrayList<>());
                            }
                            return resource;
                        });
            }
        } catch (Exception ex) {
            log.warn("Unable to clear finalizers for {} {}/{}: {}", context.getPlural(), namespace, name, ex.getMessage());
        }
    }

    private void aggressivelyDeleteMatchingCsvs(OperatorCatalogEntry entry, List<HasMetadata> resources) {
        Set<String> namespaces = collectOlmNamespaces(entry, resources);
        String normalizedPackage = normalizePackageName(entry.getPackageName());
        String normalizedEntryName = normalizePackageName(entry.getName());
        Set<String> matchers = new LinkedHashSet<>();
        if (StringUtils.hasText(normalizedPackage)) {
            matchers.add(normalizedPackage);
        }
        if (StringUtils.hasText(normalizedEntryName)) {
            matchers.add(normalizedEntryName);
        }

        ResourceDefinitionContext csvContext = new ResourceDefinitionContext.Builder()
                .withGroup("operators.coreos.com")
                .withVersion("v1alpha1")
                .withPlural("clusterserviceversions")
                .withNamespaced(true)
                .build();

        Instant deadline = Instant.now().plus(FORCE_CSV_DELETE_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            List<GenericKubernetesResource> csvs = safeList(csvContext, namespaces);
            List<GenericKubernetesResource> matches = csvs.stream()
                    .filter(Objects::nonNull)
                    .filter(csv -> csv.getMetadata() != null && StringUtils.hasText(csv.getMetadata().getName()))
                    .filter(csv -> {
                        String name = csv.getMetadata().getName();
                        return isOwnedResource(csv, entry) || csvMatchesPackage(name, matchers);
                    })
                    .toList();

            if (matches.isEmpty()) {
                return;
            }

            for (GenericKubernetesResource csv : matches) {
                String ns = csv.getMetadata().getNamespace();
                String name = csv.getMetadata().getName();
                safeClearFinalizersByName(csvContext, ns, name);
                safeDeleteByName(csvContext, ns, name);
            }

            try {
                Thread.sleep(FORCE_CSV_DELETE_SLEEP.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean matchesSubscription(GenericKubernetesResource subscription, String packageName, Set<String> subscriptionNames) {
        ObjectMeta metadata = subscription.getMetadata();
        String name = metadata == null ? null : metadata.getName();
        if (StringUtils.hasText(name) && subscriptionNames.contains(name)) {
            return true;
        }
        if (!StringUtils.hasText(packageName)) {
            return false;
        }
        Map<?, ?> spec = readMap(subscription.getAdditionalProperties().get("spec"));
        String specName = normalizePackageName(readString(spec, "name"));
        String normalizedPackageName = normalizePackageName(packageName);
        return StringUtils.hasText(normalizedPackageName) && normalizedPackageName.equals(specName);
    }

    private Map<?, ?> readMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return Map.of();
    }

    private String readString(Map<?, ?> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        String value = String.valueOf(map.get(key)).trim();
        return value.isEmpty() ? null : value;
    }

    private boolean shouldRetainCrd(OperatorCatalogEntry entry, boolean noCrsPresent) {
        if (entry.getUninstallPolicy() == OperatorUninstallPolicy.RETAIN_CRDS) {
            return true;
        }
        if (entry.getUninstallPolicy() == OperatorUninstallPolicy.DELETE_CRDS_IF_EMPTY) {
            return !noCrsPresent;
        }
        return false;
    }

    private boolean isCrd(HasMetadata resource) {
        return "CustomResourceDefinition".equalsIgnoreCase(resource.getKind());
    }

    private String resolveNamespace(HasMetadata resource, String fallbackNamespace, boolean forceFallbackNamespace) {
        if (!isLikelyNamespaced(resource)) {
            return null;
        }

        ObjectMeta metadata = resource.getMetadata();
        if (forceFallbackNamespace && StringUtils.hasText(fallbackNamespace)) {
            if (metadata != null) {
                metadata.setNamespace(fallbackNamespace);
            }
            return fallbackNamespace;
        }
        if (metadata != null && StringUtils.hasText(metadata.getNamespace())) {
            return metadata.getNamespace();
        }
        if (StringUtils.hasText(fallbackNamespace)) {
            if (metadata != null) {
                metadata.setNamespace(fallbackNamespace);
            }
            return fallbackNamespace;
        }
        return null;
    }

    private boolean isAllowedOlmSeedResource(HasMetadata resource) {
        if (!StringUtils.hasText(resource.getKind()) || !StringUtils.hasText(resource.getApiVersion())) {
            return false;
        }
        if (!ALLOWED_OLM_SEED_KINDS.contains(resource.getKind())) {
            return false;
        }
        String apiVersion = resource.getApiVersion();
        return apiVersion.startsWith("operators.coreos.com/");
    }

    private void normalizeNamespaceForInstall(HasMetadata resource, String targetNamespace) {
        resolveNamespace(resource, targetNamespace, true);
    }

    private void applyOwnershipLabels(HasMetadata resource, OperatorCatalogEntry entry) {
        if (resource == null || resource.getMetadata() == null) {
            return;
        }
        Map<String, String> labels = resource.getMetadata().getLabels();
        if (labels == null) {
            labels = new LinkedHashMap<>();
            resource.getMetadata().setLabels(labels);
        }
        labels.put(OWNERSHIP_MANAGED_BY_KEY, OWNERSHIP_MANAGED_BY_VALUE);
        if (StringUtils.hasText(entry.getId())) {
            labels.put(OWNERSHIP_CATALOG_ID_KEY, entry.getId());
        }
        String normalizedPackage = normalizePackageName(entry.getPackageName());
        if (StringUtils.hasText(normalizedPackage)) {
            labels.put(OWNERSHIP_PACKAGE_KEY, normalizedPackage);
        }
    }

    boolean isOwnedResource(GenericKubernetesResource resource, OperatorCatalogEntry entry) {
        if (resource == null || resource.getMetadata() == null || entry == null) {
            return false;
        }
        Map<String, String> labels = resource.getMetadata().getLabels();
        if (labels == null || labels.isEmpty()) {
            return false;
        }
        if (!OWNERSHIP_MANAGED_BY_VALUE.equals(trimToNull(labels.get(OWNERSHIP_MANAGED_BY_KEY)))) {
            return false;
        }
        String labeledCatalogId = trimToNull(labels.get(OWNERSHIP_CATALOG_ID_KEY));
        if (StringUtils.hasText(entry.getId()) && entry.getId().equals(labeledCatalogId)) {
            return true;
        }
        String labeledPackage = normalizePackageName(labels.get(OWNERSHIP_PACKAGE_KEY));
        String expectedPackage = normalizePackageName(entry.getPackageName());
        return StringUtils.hasText(expectedPackage) && expectedPackage.equals(labeledPackage);
    }

    private boolean isLikelyNamespaced(HasMetadata resource) {
        String kind = resource.getKind();
        if (!StringUtils.hasText(kind)) {
            return true;
        }
        return !CLUSTER_SCOPED_KINDS.contains(kind);
    }

    private Map<String, Integer> countManagedCustomResources(OperatorCatalogEntry entry) {
        Map<String, Integer> usage = new LinkedHashMap<>();
        List<ManagedCrdRef> managedCrds = entry.getManagedCrds() == null ? List.of() : entry.getManagedCrds();
        for (ManagedCrdRef managedCrd : managedCrds) {
            if (managedCrd == null) {
                continue;
            }
            if (!StringUtils.hasText(managedCrd.getGroup())
                    || !StringUtils.hasText(managedCrd.getVersion())
                    || !StringUtils.hasText(managedCrd.getPlural())) {
                continue;
            }

            ResourceDefinitionContext context = new ResourceDefinitionContext.Builder()
                    .withGroup(managedCrd.getGroup())
                    .withVersion(managedCrd.getVersion())
                    .withPlural(managedCrd.getPlural())
                    .build();

            String key = managedCrd.getPlural() + "." + managedCrd.getGroup() + "/" + managedCrd.getVersion();
            int count = 0;
            try {
                if (Boolean.TRUE.equals(managedCrd.getNamespaced())) {
                    if (StringUtils.hasText(entry.getTargetNamespace())) {
                        count = kubernetesClient.genericKubernetesResources(context)
                                .inNamespace(entry.getTargetNamespace())
                                .list()
                                .getItems()
                                .size();
                    } else {
                        count = kubernetesClient.genericKubernetesResources(context)
                                .inAnyNamespace()
                                .list()
                                .getItems()
                                .size();
                    }
                } else {
                    count = kubernetesClient.genericKubernetesResources(context)
                            .inAnyNamespace()
                            .list()
                            .getItems()
                            .size();
                }
            } catch (Exception ex) {
                log.warn("Unable to count managed CRs for {}: {}. Continuing uninstall flow.", key, ex.getMessage());
            }
            usage.put(key, count);
        }
        return usage;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizePackageName(String value) {
        String trimmed = trimToNull(value);
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        return CSV_VERSION_SUFFIX.matcher(trimmed.toLowerCase(Locale.ROOT)).replaceFirst("");
    }

    private String safeLower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
