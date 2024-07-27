package com.izylife.izykube.services;

import com.izylife.izykube.dto.cluster.*;
import com.izylife.izykube.factory.NodeFactory;
import com.izylife.izykube.factory.TemplateFactory;
import com.izylife.izykube.model.Cluster;
import com.izylife.izykube.model.ClusterTemplate;
import com.izylife.izykube.repositories.ClusterRepository;
import com.izylife.izykube.repositories.ClusterTemplateRepository;
import com.izylife.izykube.services.processors.TemplateProcessor;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import javassist.tools.rmi.ObjectNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class ClusterService {

    private final KubernetesClient client;
    private final TemplateFactory templateFactory;
    private final ClusterRepository clusterRepository;
    private final ClusterTemplateRepository clusterTemplateRepository;


    public ClusterDTO createCluster(ClusterDTO clusterDTO) {

        try {
            Cluster cluster = new Cluster();
            cluster.setName(clusterDTO.getName());
            cluster.setNodes(clusterDTO.getNodes());
            cluster.setLinks(clusterDTO.getLinks());
            cluster.setDiagram(clusterDTO.getDiagram());
            Cluster savedCluster = clusterRepository.save(cluster);

            ClusterDTO savedClusterDTO = new ClusterDTO();
            savedClusterDTO.setId(savedCluster.getId());
            savedClusterDTO.setName(savedCluster.getName());
            savedClusterDTO.setNodes(savedCluster.getNodes());
            savedClusterDTO.setLinks(savedCluster.getLinks());
            savedClusterDTO.setDiagram(savedCluster.getDiagram());

            return savedClusterDTO;

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
            Cluster updatedCluster = clusterRepository.save(cluster);

            ClusterDTO updatedClusterDTO = new ClusterDTO();
            updatedClusterDTO.setId(updatedCluster.getId());
            updatedClusterDTO.setName(updatedCluster.getName());
            updatedClusterDTO.setNameSpace(updatedCluster.getNameSpace());
            updatedClusterDTO.setNodes(updatedCluster.getNodes());
            updatedClusterDTO.setLinks(updatedCluster.getLinks());
            updatedClusterDTO.setDiagram(updatedCluster.getDiagram());

            return updatedClusterDTO;

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
        ClusterDTO dto = new ClusterDTO();

        try {
            Cluster cluster = clusterRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));

            dto.setId(cluster.getId());
            dto.setName(cluster.getName());
            dto.setNameSpace(cluster.getNameSpace());

            // Use the factory to create appropriate NodeDTOs
            List<NodeDTO> nodeDTOs = cluster.getNodes().stream()
                    .map(NodeFactory::createNodeDTO)
                    .collect(Collectors.toList());

            dto.setNodes(nodeDTOs);
            dto.setLinks(cluster.getLinks());
            dto.setDiagram(cluster.getDiagram());
        } catch (Exception e) {
            log.error("Error getting cluster with ID " + id + ": " + e.getMessage());
            return null;
        }

        return dto;
    }

    public void createTemplate(String id) throws ObjectNotFoundException {
        Cluster cluster = clusterRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));
        List<NodeDTO> nodes = cluster.getNodes();
        List<String> yamlList = new ArrayList<>();

        Set<String> processedNodes = new HashSet<>();

        for (NodeDTO node : nodes) {
            if (!processedNodes.contains(node.getId())) {
                List<NodeDTO> linkedNodes = cluster.findSourceNodesOf(node.getId());
                node.setLinkedNodes(linkedNodes);
                yamlList.add(processNodeDTO(node));

                // Mark this node and all its linked nodes as processed
                processedNodes.add(node.getId());
                linkedNodes.forEach(linkedNode -> processedNodes.add(linkedNode.getId()));
            }
        }

        ClusterTemplate clusterTemplate = new ClusterTemplate();
        clusterTemplate.setClusterId(id);
        clusterTemplate.setYamlList(yamlList);
        clusterTemplateRepository.save(clusterTemplate);
    }


    public void deploy(String clusterId) throws ObjectNotFoundException {
        // Retrieve the cluster template from the database
        ClusterTemplate template = clusterTemplateRepository.findByClusterId(clusterId)
                .orElseThrow(() -> new ObjectNotFoundException("Template not found for cluster ID: " + clusterId));

        // Deploy each YAML in the template
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
                // Optionally, you might want to throw this exception to be handled by the caller
            }
        }
    }

    public void undeploy(String clusterId) throws ObjectNotFoundException {
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
                    boolean deleted = client.resource(resource).delete();
                    if (deleted) {
                        log.info("Undeployed resource: " + resource.getKind() + "/" + resource.getMetadata().getName());
                    } else {
                        log.warn("Failed to undeploy resource: " + resource.getKind() + "/" + resource.getMetadata().getName());
                    }
                }
            } catch (KubernetesClientException e) {
                log.error("Error undeploying resource from template: " + e.getMessage());
            }
        }
    }

    private String processNodeDTO(NodeDTO nodeDTO) {
        if (nodeDTO instanceof DeploymentDTO) {
            return processSpecificNodeDTO((DeploymentDTO) nodeDTO);
        } else if (nodeDTO instanceof ConfigMapDTO) {
            return processSpecificNodeDTO((ConfigMapDTO) nodeDTO);
        } else if (nodeDTO instanceof ServiceDTO) {
            return processSpecificNodeDTO((ServiceDTO) nodeDTO);
        } else if (nodeDTO instanceof IngressDTO) {
            return processSpecificNodeDTO((IngressDTO) nodeDTO);
        } else if (nodeDTO instanceof ContainerDTO) {
            return processSpecificNodeDTO((ContainerDTO) nodeDTO);
        } else {
            throw new IllegalArgumentException("Unsupported NodeDTO type: " + nodeDTO.getClass().getSimpleName());
        }
    }

    private <T extends NodeDTO> String processSpecificNodeDTO(T specificNodeDTO) {
        TemplateProcessor<T> processor = templateFactory.getProcessor(specificNodeDTO);
        return processor.createTemplate(specificNodeDTO);
    }
}


