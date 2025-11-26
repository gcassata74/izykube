package com.izylife.izykube.services;

import com.izylife.izykube.collections.ClusterStatusEnum;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.factory.TemplateFactory;
import com.izylife.izykube.model.Cluster;
import com.izylife.izykube.model.ClusterTemplate;
import com.izylife.izykube.repositories.ClusterRepository;
import com.izylife.izykube.repositories.ClusterTemplateRepository;
import com.izylife.izykube.services.ai.ClusterYamlService;
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
    private final ClusterYamlService clusterYamlService;

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
            String namespace = Optional.ofNullable(clusterDTO.getNameSpace()).filter(ns -> !ns.isBlank()).orElse("default");

            List<NodeDTO> templateableNodes = clusterDTO.getNodes().stream()
                    .filter(this::isTemplateableResource)
                    .toList();

            templateableNodes.forEach(node -> {
                node.setNamespace(namespace);
                node.setSourceNodes(ClusterUtil.findSourceNodesOf(clusterDTO, node.getId()));
                node.setTargetNodes(ClusterUtil.findTargetNodesOf(clusterDTO, node.getId()));
            });

            templateableNodes.stream()
                    .filter(node -> !processedNodes.contains(node.getId()))
                    .forEach(node -> processNodeAndLinkedNodes(clusterDTO, node, yamlList, processedNodes));

            return saveTemplateForCluster(id, yamlList);

        } catch (Exception primaryException) {
            log.warn("Primary template generation failed for cluster {}: {}. Falling back to raw manifests.",
                    id, primaryException.getMessage());
            return createTemplateFromRawManifests(id, clusterDTO, primaryException);
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

    private ClusterTemplate saveTemplateForCluster(String clusterId, LinkedList<String> yamlList) {
        return clusterTemplateRepository.findByClusterId(clusterId)
                .map(existing -> {
                    existing.setYamlList(yamlList);
                    return clusterTemplateRepository.save(existing);
                })
                .orElseGet(() -> {
                    ClusterTemplate template = new ClusterTemplate();
                    template.setClusterId(clusterId);
                    template.setYamlList(yamlList);
                    return clusterTemplateRepository.save(template);
                });
    }

    private ClusterTemplate createTemplateFromRawManifests(String clusterId, ClusterDTO clusterDTO, Exception originalCause) {
        if (clusterDTO.getDiagram() == null || clusterDTO.getDiagram().isBlank()) {
            throw new RuntimeException("Failed to generate template for cluster: " + clusterId, originalCause);
        }
        try {
            String exportedYaml = clusterYamlService.exportCluster(clusterDTO);
            LinkedList<String> fallbackYaml = new LinkedList<>();
            fallbackYaml.add(exportedYaml);
            return saveTemplateForCluster(clusterId, fallbackYaml);
        } catch (Exception fallbackException) {
            originalCause.addSuppressed(fallbackException);
            throw new RuntimeException("Failed to generate template for cluster: " + clusterId, originalCause);
        }
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
