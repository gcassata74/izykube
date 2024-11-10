package com.izylife.izykube.services;

import com.izylife.izykube.collections.ClusterStatusEnum;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.factory.ClientFactory;
import com.izylife.izykube.factory.NodeFactory;
import com.izylife.izykube.factory.TemplateFactory;
import com.izylife.izykube.model.Cluster;
import com.izylife.izykube.model.ClusterTemplate;
import com.izylife.izykube.repositories.ClusterRepository;
import com.izylife.izykube.repositories.ClusterTemplateRepository;
import com.izylife.izykube.utils.ClusterUtil;
import io.fabric8.istio.api.networking.v1beta1.Gateway;
import io.fabric8.istio.api.networking.v1beta1.VirtualService;
import io.fabric8.istio.client.IstioClient;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import javassist.tools.rmi.ObjectNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class ClusterService {

    private final ClientFactory clientFactory;
    private final ClusterRepository clusterRepository;
    private final ClusterTemplateRepository clusterTemplateRepository;
    private final TemplateFactory templateFactory;
    private final TemplateService templateService;


    public ClusterDTO createCluster(ClusterDTO clusterDTO) {

        try {
            Cluster cluster = new Cluster();
            cluster.setName(clusterDTO.getName());
            cluster.setNodes(clusterDTO.getNodes());
            cluster.setLinks(clusterDTO.getLinks());
            cluster.setDiagram(clusterDTO.getDiagram());
            cluster.setStatus(ClusterStatusEnum.INITIALIZED);
            Cluster savedCluster = clusterRepository.save(cluster);

            return ClusterDTO.builder()
                    .id(savedCluster.getId())
                    .name(savedCluster.getName())
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
            Cluster cluster = clusterRepository.findById(clusterDTO.getId()).orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));
            cluster.setId(clusterDTO.getId());
            cluster.setName(clusterDTO.getName());
            cluster.setNameSpace(clusterDTO.getNameSpace());
            cluster.setNodes(clusterDTO.getNodes());
            cluster.setLinks(clusterDTO.getLinks());
            cluster.setDiagram(clusterDTO.getDiagram());
            cluster.setStatus(ClusterStatusEnum.CREATED);
            Cluster updatedCluster = clusterRepository.save(cluster);

            return ClusterDTO.builder()
                    .id(updatedCluster.getId())
                    .name(updatedCluster.getName())
                    .nameSpace(updatedCluster.getNameSpace())
                    .nodes(updatedCluster.getNodes())
                    .links(updatedCluster.getLinks())
                    .diagram(updatedCluster.getDiagram())
                    .build();

        } catch (Exception e) {
            log.error("Error updating cluster: " + e.getMessage());
            return null;
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
            clusterRepository.deleteById(id);
        } catch (Exception e) {
            log.error("Error deleting cluster: " + e.getMessage());
        }
    }

    public ClusterDTO getClusterById(String id) {

        try {
            Cluster cluster = clusterRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));

            // Use the factory to create appropriate NodeDTOs
            List<NodeDTO> nodeDTOs = cluster.getNodes().stream()
                    .map(NodeFactory::createNodeDTO)
                    .collect(Collectors.toList());

            ClusterDTO clusterDTO = ClusterDTO.builder()
                    .id(cluster.getId())
                    .name(cluster.getName())
                    .nameSpace(cluster.getNameSpace())
                    .nodes(nodeDTOs)
                    .links(cluster.getLinks())
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

        ClusterTemplate template = clusterTemplateRepository.findByClusterId(clusterId)
                .orElseThrow(() -> new ObjectNotFoundException("Template not found for cluster ID: " + clusterId));

        for (String yaml : template.getYamlList()) {
            try {
                KubernetesClient k8sClient = (KubernetesClient) clientFactory.getClient("kubernetes");
                List<HasMetadata> resources = k8sClient.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).items();

                for (HasMetadata resource : resources) {
                    Object client = clientFactory.getClient(resource.getApiVersion());

                    if (client instanceof IstioClient) {
                        deployIstioResource((IstioClient) client, resource);
                    } else if (client instanceof KubernetesClient) {
                        ((KubernetesClient) client).resource(resource).createOrReplace();
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

    private void deployIstioResource(IstioClient istioClient, HasMetadata resource) {
        if (resource instanceof Gateway) {
            istioClient.v1beta1().gateways().inNamespace("default").resource((Gateway) resource).create();
        } else if (resource instanceof VirtualService) {
            istioClient.v1beta1().virtualServices().inNamespace("default").resource((VirtualService) resource).create();
        } else {
            log.warn("Unsupported Istio resource type: " + resource.getKind());
        }
    }

    private void applyTemplate(ClusterTemplate template) {
        for (String yaml : template.getYamlList()) {
            try {
                // Load the YAML into Kubernetes resources
                KubernetesClient k8sClient = (KubernetesClient) clientFactory.getClient("kubernetes");
                List<HasMetadata> resources = k8sClient.load(new ByteArrayInputStream(yaml.getBytes())).items();

                // Create or update each resource
                for (HasMetadata resource : resources) {
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

        ClusterTemplate template = clusterTemplateRepository.findByClusterId(clusterId)
                .orElseThrow(() -> new ObjectNotFoundException("Template not found for cluster ID: " + clusterId));

        for (String yaml : template.getYamlList()) {
            try {
                KubernetesClient k8sClient = (KubernetesClient) clientFactory.getClient("kubernetes");
                List<HasMetadata> resources = k8sClient.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).get();

                if (resources == null || resources.isEmpty()) {
                    log.error("No resources found in YAML: " + yaml);
                    continue;
                }

                for (HasMetadata resource : resources) {
                    if (resource == null) {
                        log.error("Null resource found in YAML: " + yaml);
                        continue;
                    }

                    Object client = clientFactory.getClient(resource.getApiVersion());

                    if (client instanceof IstioClient) {
                        undeployIstioResource((IstioClient) client, resource);
                    } else if (client instanceof KubernetesClient) {
                        ((KubernetesClient) client).resource(resource).delete();
                    }

                    log.info("Deleted resource: " + resource.getKind() + "/" + resource.getMetadata().getName());
                }

            } catch (KubernetesClientException e) {
                log.error("Error undeploying resource from template: " + e.getMessage());
            }
        }

        cluster.setStatus(ClusterStatusEnum.READY_FOR_DEPLOYMENT);
        clusterRepository.save(cluster);
    }

    private void undeployIstioResource(IstioClient istioClient, HasMetadata resource) {
        if (resource instanceof Gateway) {
            istioClient.v1beta1().gateways().inNamespace("default").resource((Gateway) resource).delete();
        } else if (resource instanceof VirtualService) {
            istioClient.v1beta1().virtualServices().inNamespace("default").resource((VirtualService) resource).delete();
        } else {
            log.warn("Unsupported Istio resource type: " + resource.getKind());
        }
    }


    public Cluster patchCluster(String id, ClusterDTO clusterDTO) {
        try {
            // Find the existing cluster
            Cluster existingCluster = clusterRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Cluster not found with id: " + id));

            // Generate and save the template
            ClusterTemplate template = templateService.createOrReplaceTemplate(id, clusterDTO);
            if (template == null) {
                throw new IllegalStateException("Failed to generate template for cluster: " + id);
            }

            applyTemplate(template);
            triggerDeploymentUpdates(clusterDTO);

            // Update the cluster entity with new data
            existingCluster.setName(clusterDTO.getName());
            existingCluster.setNameSpace(clusterDTO.getNameSpace());
            existingCluster.setNodes(clusterDTO.getNodes());
            existingCluster.setLinks(clusterDTO.getLinks());
            existingCluster.setDiagram(clusterDTO.getDiagram());
            //keep the status as it is
            existingCluster.setStatus(existingCluster.getStatus());
            // Save the updated cluster
            Cluster updatedCluster = clusterRepository.save(existingCluster);
            return updatedCluster;


        } catch (ObjectNotFoundException e) {
            throw new RuntimeException("Failed to patch cluster: " + id, e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error while patching cluster: " + id, e);
        }
    }

    private void triggerDeploymentUpdates(ClusterDTO clusterDTO) {
        List<NodeDTO> configMaps = ClusterUtil.findNodesByKind(clusterDTO, "configmap");

        for (NodeDTO configMap : configMaps) {
            List<NodeDTO> connectedDeployments = ClusterUtil.findTargetNodesOf(clusterDTO, configMap.getId())
                    .stream()
                    .filter(node -> "deployment".equals(node.getKind()))
                    .collect(Collectors.toList());

            for (NodeDTO deployment : connectedDeployments) {
                restartDeployment(deployment.getName());
            }
        }
    }

    private void restartDeployment(String name) {
        // Implement the logic to trigger a rolling update for the deployment
        // Example using Fabric8 Kubernetes client:
        KubernetesClient k8sClient = (KubernetesClient) clientFactory.getClient("kubernetes");
        k8sClient.apps().deployments()
                .inNamespace("default")
                .withName(name)
                .edit(d -> new DeploymentBuilder(d)
                        .editSpec().editTemplate().editMetadata()
                        .addToAnnotations("kubectl.kubernetes.io/restartedAt", Instant.now().toString())
                        .endMetadata().endTemplate().endSpec()
                        .build());
    }
}


