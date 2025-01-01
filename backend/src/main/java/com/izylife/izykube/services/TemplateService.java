package com.izylife.izykube.services;

import com.izylife.izykube.collections.ClusterStatusEnum;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.factory.TemplateFactory;
import com.izylife.izykube.model.Cluster;
import com.izylife.izykube.model.ClusterTemplate;
import com.izylife.izykube.repositories.ClusterRepository;
import com.izylife.izykube.repositories.ClusterTemplateRepository;
import com.izylife.izykube.services.processors.TemplateProcessor;
import com.izylife.izykube.utils.ClusterUtil;
import com.izylife.izykube.utils.TemplatableResourceUtil;
import javassist.tools.rmi.ObjectNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@AllArgsConstructor
@Service
@Slf4j
public class TemplateService {

    private final TemplateFactory templateFactory;
    private final ClusterRepository clusterRepository;
    private final ClusterTemplateRepository clusterTemplateRepository;

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

        ClusterTemplate template = createOrReplaceTemplate(id, clusterDTO);
        clusterTemplateRepository.save(template);
        cluster.setStatus(ClusterStatusEnum.READY_FOR_DEPLOYMENT);
        clusterRepository.save(cluster);

    }

    protected ClusterTemplate createOrReplaceTemplate(String id, ClusterDTO clusterDTO) {
        LinkedList<String> yamlList = new LinkedList<>();
        Set<String> processedNodes = new HashSet<>();

        try {
            List<NodeDTO> templateableNodes = clusterDTO.getNodes().stream()
                    .filter(this::isTemplateableResource)
                    .toList();

            // Set dependencies for templatable nodes
            templateableNodes.forEach(node -> {
                node.setSourceNodes(ClusterUtil.findSourceNodesOf(clusterDTO, node.getId()));
                node.setTargetNodes(ClusterUtil.findTargetNodesOf(clusterDTO, node.getId()));
            });

            // Process nodes
            templateableNodes.stream()
                    .filter(node -> !processedNodes.contains(node.getId()))
                    .forEach(node -> processNodeAndLinkedNodes(clusterDTO, node, yamlList, processedNodes));

            // Create or update template
            return clusterTemplateRepository.findByClusterId(id)
                    .map(template -> {
                        template.setYamlList(yamlList);
                        return clusterTemplateRepository.save(template);
                    })
                    .orElseGet(() -> {
                        ClusterTemplate template = new ClusterTemplate();
                        template.setClusterId(id);
                        template.setYamlList(yamlList);
                        return clusterTemplateRepository.save(template);
                    });

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate template for cluster: " + id, e);
        }
    }


    private List<NodeDTO> orderNodesAncestorsFirst(ClusterDTO clusterDTO) {
        List<NodeDTO> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        // Start with nodes that have no dependencies (no source nodes)
        List<NodeDTO> startNodes = clusterDTO.getNodes().stream()
                .filter(node -> ClusterUtil.findSourceNodesOf(clusterDTO, node.getId()).isEmpty())
                .toList();

        for (NodeDTO node : startNodes) {
            if (!visited.contains(node.getId())) {
                topologicalSort(node, clusterDTO, result, visited, visiting);
            }
        }

        return result;
    }

    private void topologicalSort(NodeDTO node, ClusterDTO clusterDTO, List<NodeDTO> result,
                                 Set<String> visited, Set<String> visiting) {
        visiting.add(node.getId());

        List<NodeDTO> targetNodes = ClusterUtil.findTargetNodesOf(clusterDTO, node.getId());
        for (NodeDTO targetNode : targetNodes) {
            if (visiting.contains(targetNode.getId())) {
                throw new IllegalStateException("Circular dependency detected between " +
                        node.getName() + " and " + targetNode.getName());
            }
            if (!visited.contains(targetNode.getId())) {
                topologicalSort(targetNode, clusterDTO, result, visited, visiting);
            }
        }

        visiting.remove(node.getId());
        visited.add(node.getId());
        result.add(0, node); // Add to beginning for correct order
    }

    private boolean isTemplateableResource(NodeDTO node) {
        return TemplatableResourceUtil.isTemplatable(node.getKind());
    }

    private void processNodeAndLinkedNodes(ClusterDTO clusterDTO, NodeDTO node, List<String> yamlList, Set<String> processedNodes) {
        if (processedNodes.contains(node.getId())) {
            return;
        }
        processedNodes.add(node.getId());
        String yaml = processSpecificNodeDTO(node);
        if (yaml != null && !yaml.isEmpty()) {
            yamlList.add(yaml);
        }
    }

    private String processSpecificNodeDTO(NodeDTO node) {
        TemplateProcessor<NodeDTO> processor = templateFactory.getProcessor(node);
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

    public void updateTemplate(String id, ClusterDTO clusterDTO) {
        createOrReplaceTemplate(id, clusterDTO);
    }

    private Cluster createDetachedCluster(ClusterDTO cluster) {
        Cluster detachedCluster = new Cluster();
        detachedCluster.setName(cluster.getName());
        detachedCluster.setNameSpace(cluster.getNameSpace());
        detachedCluster.setCreationDate(new Date());
        detachedCluster.setLastUpdated(new Date());
        detachedCluster.setNodes(cluster.getNodes());
        detachedCluster.setLinks(cluster.getLinks());
        detachedCluster.setDiagram(cluster.getDiagram());
        return detachedCluster;
    }

}
