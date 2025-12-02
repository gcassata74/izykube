package com.izylife.izykube.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.DeploymentDTO;
import com.izylife.izykube.dto.cluster.DeploymentWorkloadType;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.ResourceSyncStatusDTO;
import com.izylife.izykube.factory.ClientFactory;
import com.izylife.izykube.factory.NodeFactory;
import com.izylife.izykube.factory.TemplateFactory;
import com.izylife.izykube.model.Cluster;
import com.izylife.izykube.model.Namespace;
import com.izylife.izykube.repositories.ClusterRepository;
import com.izylife.izykube.repositories.NamespaceRepository;
import com.izylife.izykube.utils.ClusterUtil;
import io.fabric8.istio.api.networking.v1beta1.Gateway;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import io.fabric8.istio.client.IstioClient;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import javassist.tools.rmi.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class NamespaceResourceService {

    private static final Set<String> CLUSTER_SCOPED_KINDS = new HashSet<>(Set.of(
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
    ));

    private final NamespaceRepository namespaceRepository;
    private final ClusterRepository clusterRepository;
    private final TemplateFactory templateFactory;
    private final ClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    private static final long RESOURCE_SYNC_TIMEOUT_MS = Duration.ofMinutes(5).toMillis();
    private static final long RESOURCE_SYNC_POLL_INTERVAL_MS = Duration.ofSeconds(3).toMillis();

    public ResourceSyncStatusDTO restartResource(String namespaceIdentifier, String resourceId, NodeDTO nodePayload) throws ObjectNotFoundException {
        if (nodePayload == null) {
            throw new IllegalArgumentException("Resource payload is required");
        }

        String namespace = resolveNamespaceName(namespaceIdentifier);
        Cluster cluster = clusterRepository.findByNameSpaceIgnoreCase(namespace)
                .orElseThrow(() -> new ObjectNotFoundException("Cluster not found for namespace: " + namespace));

        NodeDTO sanitizedNode = NodeFactory.createNodeDTO(nodePayload);
        sanitizedNode.setId(resourceId);

        ClusterDTO clusterDTO = ClusterUtil.convertToDTO(cluster);
        List<NodeDTO> nodes = clusterDTO.getNodes() != null ? new ArrayList<>(clusterDTO.getNodes()) : new ArrayList<>();

        boolean replaced = false;
        for (int i = 0; i < nodes.size(); i++) {
            if (resourceId.equals(nodes.get(i).getId())) {
                nodes.set(i, sanitizedNode);
                replaced = true;
                break;
            }
        }

        if (!replaced) {
            throw new ObjectNotFoundException("Resource " + resourceId + " not found inside namespace " + namespace);
        }

        clusterDTO.setNodes(nodes);

        sanitizedNode.setNamespace(namespace);
        sanitizedNode.setSourceNodes(ClusterUtil.findSourceNodesOf(clusterDTO, resourceId));
        sanitizedNode.setTargetNodes(ClusterUtil.findTargetNodesOf(clusterDTO, resourceId));

        sanitizedNode.setNamespace(namespace);
        sanitizedNode.setSourceNodes(ClusterUtil.findSourceNodesOf(clusterDTO, resourceId));
        sanitizedNode.setTargetNodes(ClusterUtil.findTargetNodesOf(clusterDTO, resourceId));

        sanitizedNode.setAffected(true);
        cluster.setNodes(nodes);
        cluster.setDiagram(updateAffectedFlag(cluster.getDiagram(), resourceId, true));
        cluster.setLastUpdated(new Date());
        clusterRepository.save(cluster);

        applyResourceYaml(sanitizedNode, namespace);

        SyncOutcome syncOutcome = waitForResourceSynchronization(sanitizedNode, namespace);
        if (syncOutcome.synced()) {
            sanitizedNode.setAffected(false);
            cluster.setNodes(nodes);
            cluster.setDiagram(updateAffectedFlag(cluster.getDiagram(), resourceId, false));
            cluster.setLastUpdated(new Date());
            clusterRepository.save(cluster);
        } else {
            log.info("Resource {} still synchronizing in namespace {}: {}", sanitizedNode.getName(), namespace, syncOutcome.message());
        }

        String message = Optional.ofNullable(sanitizedNode.getName())
                .map(name -> name + " synchronized with cluster")
                .orElse("Resource synchronized with cluster");
        if (StringUtils.hasText(syncOutcome.message())) {
            message = syncOutcome.message();
        }
        return new ResourceSyncStatusDTO(resourceId, syncOutcome.synced(), message);
    }

    private void applyResourceYaml(NodeDTO node, String namespace) {
        try {
            var processor = templateFactory.getProcessor(node);
            String yaml = processor.createTemplate(node);
            KubernetesClient loaderClient = (KubernetesClient) clientFactory.getClient("kubernetes");
            List<HasMetadata> resources = loaderClient
                    .load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)))
                    .items();

            if (resources == null || resources.isEmpty()) {
                log.warn("No Kubernetes resources generated for {}", node.getName());
                return;
            }

            for (HasMetadata resource : resources) {
                if (resource == null) {
                    continue;
                }
                applyNamespaceMetadata(resource, namespace);
                Object client = clientFactory.getClient(resource.getApiVersion());
                if (client instanceof IstioClient istioClient) {
                    applyIstioResource(istioClient, resource, namespace);
                } else if (client instanceof KubernetesClient kubernetesClient) {
                    kubernetesClient.resource(resource).createOrReplace();
                }
            }
        } catch (KubernetesClientException ex) {
            log.error("Unable to re-apply resource {}: {}", node.getName(), ex.getMessage());
            throw ex;
        }
    }

    private void applyIstioResource(IstioClient client, HasMetadata resource, String namespace) {
        String targetNamespace = (namespace == null || namespace.isBlank()) ? "default" : namespace;
        if (resource instanceof Gateway gateway) {
            client.v1beta1().gateways().inNamespace(targetNamespace).resource(gateway).createOrReplace();
        } else if (resource instanceof VirtualService virtualService) {
            client.v1beta1().virtualServices().inNamespace(targetNamespace).resource(virtualService).createOrReplace();
        } else {
            log.warn("Unsupported Istio resource type: {}", resource.getKind());
        }
    }

    private void applyNamespaceMetadata(HasMetadata resource, String namespace) {
        if (resource == null || !StringUtils.hasText(namespace)) {
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

    private boolean isClusterScopedKind(String kind) {
        if (kind == null) {
            return false;
        }
        return CLUSTER_SCOPED_KINDS.contains(kind.toLowerCase(Locale.ROOT));
    }

    private String resolveNamespaceName(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return "default";
        }
        return namespaceRepository.findById(identifier)
                .map(Namespace::getName)
                .or(() -> namespaceRepository.findByNameIgnoreCase(identifier).map(Namespace::getName))
                .orElse(identifier);
    }

    private SyncOutcome waitForResourceSynchronization(NodeDTO node, String namespace) {
        if (node == null || !StringUtils.hasText(node.getKind())) {
            return SyncOutcome.success("Resource type missing, assuming synchronized");
        }
        if (node instanceof DeploymentDTO deployment) {
            DeploymentWorkloadType workloadType = deployment.resolveWorkloadType();
            return switch (workloadType) {
                case STATEFULSET -> waitForStatefulSetReady(deployment.getName(), namespace);
                case DAEMONSET -> waitForDaemonSetReady(deployment.getName(), namespace);
                default -> waitForDeploymentReady(deployment.getName(), namespace);
            };
        }
        return switch (node.getKind().toLowerCase(Locale.ROOT)) {
            case "deployment" -> waitForDeploymentReady(node.getName(), namespace);
            case "statefulset" -> waitForStatefulSetReady(node.getName(), namespace);
            case "daemonset" -> waitForDaemonSetReady(node.getName(), namespace);
            case "job" -> waitForJobCompletion(node.getName(), namespace);
            case "pvc", "persistentvolumeclaim" -> waitForPvcBound(node.getName(), namespace);
            default -> SyncOutcome.success("Configuration applied");
        };
    }

    private SyncOutcome waitForDeploymentReady(String resourceName, String namespace) {
        if (!StringUtils.hasText(resourceName)) {
            return SyncOutcome.success("Deployment name missing, skipping readiness check");
        }
        KubernetesClient client = (KubernetesClient) clientFactory.getClient("kubernetes");
        boolean ready = waitWithPolling(() -> {
            var deployment = client.apps().deployments().inNamespace(namespace).withName(resourceName).get();
            if (deployment == null || deployment.getStatus() == null || deployment.getSpec() == null) {
                return false;
            }
            Integer desired = Optional.ofNullable(deployment.getSpec().getReplicas()).orElse(0);
            return desired.equals(Optional.ofNullable(deployment.getStatus().getAvailableReplicas()).orElse(0))
                    && desired.equals(Optional.ofNullable(deployment.getStatus().getReadyReplicas()).orElse(0))
                    && desired.equals(Optional.ofNullable(deployment.getStatus().getUpdatedReplicas()).orElse(0));
        });
        return ready
                ? SyncOutcome.success(resourceName + " rollout completed")
                : SyncOutcome.pending("Waiting for deployment " + resourceName + " to finish rolling out");
    }

    private SyncOutcome waitForStatefulSetReady(String resourceName, String namespace) {
        if (!StringUtils.hasText(resourceName)) {
            return SyncOutcome.success("StatefulSet name missing, skipping readiness check");
        }
        KubernetesClient client = (KubernetesClient) clientFactory.getClient("kubernetes");
        boolean ready = waitWithPolling(() -> {
            var statefulSet = client.apps().statefulSets().inNamespace(namespace).withName(resourceName).get();
            if (statefulSet == null || statefulSet.getStatus() == null || statefulSet.getSpec() == null) {
                return false;
            }
            Integer desired = Optional.ofNullable(statefulSet.getSpec().getReplicas()).orElse(0);
            return desired.equals(Optional.ofNullable(statefulSet.getStatus().getReadyReplicas()).orElse(0))
                    && desired.equals(Optional.ofNullable(statefulSet.getStatus().getUpdatedReplicas()).orElse(0));
        });
        return ready
                ? SyncOutcome.success(resourceName + " stateful rollout completed")
                : SyncOutcome.pending("Waiting for statefulset " + resourceName + " to finish updating");
    }

    private SyncOutcome waitForDaemonSetReady(String resourceName, String namespace) {
        if (!StringUtils.hasText(resourceName)) {
            return SyncOutcome.success("DaemonSet name missing, skipping readiness check");
        }
        KubernetesClient client = (KubernetesClient) clientFactory.getClient("kubernetes");
        boolean ready = waitWithPolling(() -> {
            var daemonSet = client.apps().daemonSets().inNamespace(namespace).withName(resourceName).get();
            if (daemonSet == null || daemonSet.getStatus() == null) {
                return false;
            }
            Integer desired = Optional.ofNullable(daemonSet.getStatus().getDesiredNumberScheduled()).orElse(0);
            Integer readyReplicas = Optional.ofNullable(daemonSet.getStatus().getNumberReady()).orElse(0);
            return desired.equals(readyReplicas);
        });
        return ready
                ? SyncOutcome.success(resourceName + " daemonset updated")
                : SyncOutcome.pending("Waiting for daemonset " + resourceName + " pods to become ready");
    }

    private SyncOutcome waitForJobCompletion(String resourceName, String namespace) {
        if (!StringUtils.hasText(resourceName)) {
            return SyncOutcome.success("Job name missing, skipping readiness check");
        }
        KubernetesClient client = (KubernetesClient) clientFactory.getClient("kubernetes");
        boolean ready = waitWithPolling(() -> {
            var job = client.batch().v1().jobs().inNamespace(namespace).withName(resourceName).get();
            if (job == null || job.getStatus() == null) {
                return false;
            }
            Integer completions = Optional.ofNullable(job.getSpec()).map(spec -> spec.getCompletions()).orElse(1);
            Integer succeeded = Optional.ofNullable(job.getStatus().getSucceeded()).orElse(0);
            return succeeded >= completions;
        });
        return ready
                ? SyncOutcome.success(resourceName + " job completed")
                : SyncOutcome.pending("Waiting for job " + resourceName + " to complete");
    }

    private SyncOutcome waitForPvcBound(String resourceName, String namespace) {
        if (!StringUtils.hasText(resourceName)) {
            return SyncOutcome.success("PVC name missing, skipping readiness check");
        }
        KubernetesClient client = (KubernetesClient) clientFactory.getClient("kubernetes");
        boolean ready = waitWithPolling(() -> {
            var pvc = client.persistentVolumeClaims().inNamespace(namespace).withName(resourceName).get();
            if (pvc == null || pvc.getStatus() == null) {
                return false;
            }
            return "Bound".equalsIgnoreCase(pvc.getStatus().getPhase());
        });
        return ready
                ? SyncOutcome.success(resourceName + " PVC bound")
                : SyncOutcome.pending("Waiting for PVC " + resourceName + " to bind");
    }

    private boolean waitWithPolling(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + RESOURCE_SYNC_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(RESOURCE_SYNC_POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private String updateAffectedFlag(String diagramJson, String resourceId, boolean affected) {
        if (!StringUtils.hasText(diagramJson)) {
            return diagramJson;
        }
        try {
            JsonNode root = objectMapper.readTree(diagramJson);
            JsonNode nodes = root.get("nodes");
            if (nodes instanceof ArrayNode arrayNode) {
                for (JsonNode node : arrayNode) {
                    String nodeId = node.path("id").asText(null);
                    if (resourceId.equals(nodeId) && node instanceof ObjectNode objectNode) {
                        objectNode.put("isAffected", affected);
                    }
                }
                return objectMapper.writeValueAsString(root);
            }
        } catch (Exception ex) {
            log.warn("Unable to update diagram affected state for {}: {}", resourceId, ex.getMessage());
        }
        return diagramJson;
    }

    private record SyncOutcome(boolean synced, String message) {
        static SyncOutcome success(String message) {
            return new SyncOutcome(true, message);
        }

        static SyncOutcome pending(String message) {
            return new SyncOutcome(false, message);
        }
    }
}
