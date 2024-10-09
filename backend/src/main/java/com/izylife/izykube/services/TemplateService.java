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

        createOrReplaceTemplate(id, clusterDTO);
        cluster.setStatus(ClusterStatusEnum.READY_FOR_DEPLOYMENT);
        clusterRepository.save(cluster);
    }

    protected ClusterTemplate createOrReplaceTemplate(String id, ClusterDTO clusterDTO) {
        LinkedList<String> yamlList = new LinkedList<>();
        Set<String> processedNodes = new HashSet<>();

        try {
            List<NodeDTO> orderedNodes = orderNodesAncestorsFirst(clusterDTO);

            for (NodeDTO node : orderedNodes) {
                if (isTemplateableResource(node) && !processedNodes.contains(node.getId())) {
                    processNodeAndLinkedNodes(clusterDTO, node, yamlList, processedNodes);
                }
            }

            Optional<ClusterTemplate> existingTemplate = clusterTemplateRepository.findByClusterId(id);
            ClusterTemplate template = null;
            if (existingTemplate.isPresent()) {
                // Update the existing template
                template = existingTemplate.get();
                template.setYamlList(yamlList);
            } else {
                // Create a new template
                template = new ClusterTemplate();
                template.setClusterId(id);
                template.setYamlList(yamlList);
            }
            return clusterTemplateRepository.save(template);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate and save template for cluster: " + id, e);
        }
    }

    private List<NodeDTO> orderNodesAncestorsFirst(ClusterDTO clusterDTO) {
        List<NodeDTO> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (NodeDTO node : clusterDTO.getNodes()) {
            if (!visited.contains(node.getId())) {
                topologicalSortWithDependencyCheck(node, clusterDTO, result, visited, visiting);
            }
        }
        return result;
    }

    private void topologicalSortWithDependencyCheck(NodeDTO node, ClusterDTO clusterDTO, List<NodeDTO> result,
                                                    Set<String> visited, Set<String> visiting) {
        visiting.add(node.getId());

        List<NodeDTO> sourceNodes = ClusterUtil.findSourceNodesOf(clusterDTO, node.getId());
        for (NodeDTO sourceNode : sourceNodes) {
            if (visiting.contains(sourceNode.getId())) {
                throw new IllegalStateException("Circular dependency detected between " + node.getName() + " and " + sourceNode.getName());
            }
            if (!visited.contains(sourceNode.getId())) {
                topologicalSortWithDependencyCheck(sourceNode, clusterDTO, result, visited, visiting);
            }
        }

        visiting.remove(node.getId());
        visited.add(node.getId());
        result.add(node);
    }

    private boolean isTemplateableResource(NodeDTO node) {
        return TemplatableResourceUtil.isTemplatable(node.getKind());
    }

    private void processNodeAndLinkedNodes(ClusterDTO clusterDTO, NodeDTO node, List<String> yamlList, Set<String> processedNodes) {
        if (processedNodes.contains(node.getId())) {
            return;
        }
        List<NodeDTO> sourceNodes = ClusterUtil.findSourceNodesOf(clusterDTO, node.getId());

        // Now process the current node
        node.setSourceNodes(sourceNodes);
        String yaml = processSpecificNodeDTO(node);
        if (yaml != null && !yaml.isEmpty()) {
            yamlList.add(yaml);
        }
        processedNodes.add(node.getId());
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
}
