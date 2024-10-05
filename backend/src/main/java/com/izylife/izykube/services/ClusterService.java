package com.izylife.izykube.services;

import com.izylife.izykube.collections.ClusterStatusEnum;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.factory.NodeFactory;
import com.izylife.izykube.factory.TemplateFactory;
import com.izylife.izykube.model.Cluster;
import com.izylife.izykube.model.ClusterTemplate;
import com.izylife.izykube.repositories.ClusterRepository;
import com.izylife.izykube.repositories.ClusterTemplateRepository;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import javassist.tools.rmi.ObjectNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class ClusterService {

    private final KubernetesClient client;
    private final TemplateFactory templateFactory;
    private final ClusterRepository clusterRepository;
    private final ClusterTemplateRepository clusterTemplateRepository;
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

        // Retrieve the cluster template from the database
        ClusterTemplate template = clusterTemplateRepository.findByClusterId(clusterId)
                .orElseThrow(() -> new ObjectNotFoundException("Template not found for cluster ID: " + clusterId));

        // Deploy each YAML in the template
        applyTemplate(template);
        cluster.setStatus(ClusterStatusEnum.DEPLOYED);
        clusterRepository.save(cluster);
    }

    private void applyTemplate(ClusterTemplate template) {
        for (String yaml : template.getYamlList()) {
            try {
                // Load the YAML into Kubernetes resources
                List<HasMetadata> resources = client.load(new ByteArrayInputStream(yaml.getBytes())).get();

                // Create or update each resource
                for (HasMetadata resource : resources) {
                    client.resource(resource).createOrReplace();
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

        // Retrieve the cluster template from the database
        ClusterTemplate template = clusterTemplateRepository.findByClusterId(clusterId)
                .orElseThrow(() -> new ObjectNotFoundException("Template not found for cluster ID: " + clusterId));

        // Undeploy each YAML in the template
        for (String yaml : template.getYamlList()) {
            try {
                // Load the YAML into Kubernetes resources
                List<HasMetadata> resources = client.load(new ByteArrayInputStream(yaml.getBytes())).get();

                // Delete each resource
                for (HasMetadata resource : resources) {
                    client.resource(resource).delete();
                }

            } catch (KubernetesClientException e) {
                log.error("Error undeploying resource from template: " + e.getMessage());
            }
        }
        cluster.setStatus(ClusterStatusEnum.READY_FOR_DEPLOYMENT);
        clusterRepository.save(cluster);
    }

    public ClusterDTO patchCluster(String id, ClusterDTO clusterDTO) {
        try {
            // Find the existing cluster
            Cluster existingCluster = clusterRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Cluster not found with id: " + id));

            // Generate and save the template
            ClusterTemplate template = templateService.generateAndSaveTemplate(id, clusterDTO);
            if (template == null) {
                throw new IllegalStateException("Failed to generate template for cluster: " + id);
            }

            // Apply the template to the Kubernetes cluster
            for (String yaml : template.getYamlList()) {
                try {
                    // Load the YAML into Kubernetes resources
                    List<HasMetadata> resources = client.load(new ByteArrayInputStream(yaml.getBytes())).get();

                    // Patch each resource
                    for (HasMetadata resource : resources) {
                        String kind = resource.getKind();
                        String name = resource.getMetadata().getName();
                        String namespace = resource.getMetadata().getNamespace();

                        // If namespace is null, use "default" or appropriate default namespace
                        if (namespace == null) {
                            namespace = "default";
                        }
/*
                        HasMetadata patchedResource = client.resource(resource)
                                .inNamespace(namespace)
                                .patch();*/

                        log.info("Patched resource: " + kind + "/" + name + " in namespace " + namespace);
                    }
                } catch (KubernetesClientException e) {
                    log.error("Error patching resource from template: " + e.getMessage());
                }
            }

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

            // Return the updated cluster as DTO
            return ClusterDTO.builder()
                    .id(updatedCluster.getId())
                    .name(updatedCluster.getName())
                    .nameSpace(updatedCluster.getNameSpace())
                    .nodes(updatedCluster.getNodes())
                    .links(updatedCluster.getLinks())
                    .diagram(updatedCluster.getDiagram())
                    .status(updatedCluster.getStatus())
                    .build();
        } catch (ObjectNotFoundException e) {
            throw new RuntimeException("Failed to patch cluster: " + id, e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error while patching cluster: " + id, e);
        }
    }
}


