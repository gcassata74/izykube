package com.izylife.izykube.services;

import com.izylife.izykube.collections.ClusterStatusEnum;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.ClusterDTOHelper;
import com.izylife.izykube.dto.cluster.NodeDTO;
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
                    .build();

            return clusterDTO;

        } catch (Exception e) {
            log.error("Error getting cluster with ID " + id + ": " + e.getMessage());
            return null;
        }

    }

    public void createTemplate(String id) throws ObjectNotFoundException {
        Cluster cluster = clusterRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));

        ClusterDTO clusterDTO = ClusterDTO.builder()
                .id(cluster.getId())
                .name(cluster.getName())
                .nodes(cluster.getNodes())
                .links(cluster.getLinks())
                .diagram(cluster.getDiagram())
                .build();

        List<String> yamlList = new ArrayList<>();
        Set<String> processedNodes = new HashSet<>();

        // Process all nodes in the order they appear in the cluster
        for (NodeDTO node : cluster.getNodes()) {
            processNodeAndLinkedNodes(clusterDTO, node, yamlList, processedNodes);
        }

        ClusterTemplate clusterTemplate = new ClusterTemplate();
        clusterTemplate.setClusterId(id);
        clusterTemplate.setYamlList(yamlList);
        cluster.setStatus(ClusterStatusEnum.READY_FOR_DEPLOYMENT);

        clusterRepository.save(cluster);
        clusterTemplateRepository.save(clusterTemplate);
    }

    private void processNodeAndLinkedNodes(ClusterDTO clusterDTO, NodeDTO node, List<String> yamlList, Set<String> processedNodes) {
        if (processedNodes.contains(node.getId())) {
            return;
        }

        List<NodeDTO> linkedNodes = ClusterDTOHelper.findSourceNodesOf(clusterDTO, node.getId());

        // Process linked nodes first
        for (NodeDTO linkedNode : linkedNodes) {
            processNodeAndLinkedNodes(clusterDTO, linkedNode, yamlList, processedNodes);
        }

        // Now process the current node
        node.setLinkedNodes(linkedNodes);
        String yaml = processSpecificNodeDTO(node);
        if (yaml != null && !yaml.isEmpty()) {
            yamlList.add(yaml);
        }
        processedNodes.add(node.getId());
    }

    private String processSpecificNodeDTO(NodeDTO node) {
        TemplateProcessor processor = templateFactory.getProcessor(node);
        return processor.createTemplate(node);
    }

    public void deleteTemplate(String clusterId) throws ObjectNotFoundException {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));
        ClusterTemplate template = clusterTemplateRepository.findByClusterId(clusterId)
                .orElseThrow(() -> new ObjectNotFoundException("Template not found for cluster ID: " + clusterId));
        clusterTemplateRepository.delete(template);
        cluster.setStatus(ClusterStatusEnum.CREATED);
        clusterRepository.save(cluster);
    }

    public void deploy(String clusterId) throws ObjectNotFoundException {

        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ObjectNotFoundException("Cluster not found"));

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
                cluster.setStatus(ClusterStatusEnum.DEPLOYED);
                clusterRepository.save(cluster);
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
        cluster.setStatus(ClusterStatusEnum.READY_FOR_DEPLOYMENT);
        clusterRepository.save(cluster);
    }

}


