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
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.ServiceAccountDTO;
import com.izylife.izykube.factory.TemplateFactory;
import com.izylife.izykube.model.Cluster;
import com.izylife.izykube.model.ClusterTemplate;
import com.izylife.izykube.repositories.ClusterRepository;
import com.izylife.izykube.repositories.ClusterTemplateRepository;
import com.izylife.izykube.services.ai.ClusterYamlService;
import com.izylife.izykube.services.processors.RbacProcessor;
import com.izylife.izykube.services.processors.TemplateProcessor;
import com.izylife.izykube.utils.ClusterUtil;
import com.izylife.izykube.utils.TemplatableResourceUtil;
import javassist.tools.rmi.ObjectNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class TemplateService {

    private final TemplateFactory templateFactory;
    private final ClusterRepository clusterRepository;
    private final ClusterTemplateRepository clusterTemplateRepository;
    private final ClusterYamlService clusterYamlService;
    private final RbacProcessor rbacProcessor;

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

            List<NodeDTO> templateableNodes = orderNodesAncestorsFirst(clusterDTO).stream()
                    .filter(this::isTemplateableResource)
                    .toList();

            Map<String, NodeDTO> nodesById = Optional.ofNullable(clusterDTO.getNodes())
                    .orElse(List.of())
                    .stream()
                    .filter(node -> node != null && node.getId() != null)
                    .collect(Collectors.toMap(NodeDTO::getId, node -> node, (a, b) -> a, LinkedHashMap::new));

            enforceServiceAccountConstraints(nodesById, namespace);

            RbacProcessor.Generation rbacGeneration = rbacProcessor.generateAndApply(namespace, clusterDTO.getNodes(), clusterDTO.getLinks());
            yamlList.addAll(rbacGeneration.yamls());

            templateableNodes.forEach(node -> {
                if (node instanceof ServiceAccountDTO serviceAccount) {
                    if (serviceAccount.getNamespace() == null || serviceAccount.getNamespace().isBlank()) {
                        serviceAccount.setNamespace(namespace);
                    }
                } else {
                    node.setNamespace(namespace);
                }
                node.setNodeIndex(nodesById);
                node.setSourceNodes(ClusterUtil.findSourceNodesOf(clusterDTO, node.getId()));
                node.setTargetNodes(ClusterUtil.findTargetNodesOf(clusterDTO, node.getId()));
                node.setIncomingLinks(ClusterUtil.findLinksByTarget(clusterDTO, node.getId()));
                node.setOutgoingLinks(ClusterUtil.findLinksBySource(clusterDTO, node.getId()));
            });

            templateableNodes.stream()
                    .filter(node -> !processedNodes.contains(node.getId()))
                    .forEach(node -> processNodeAndLinkedNodes(clusterDTO, node, yamlList, processedNodes));

            return saveTemplateForCluster(id, yamlList);

        } catch (IllegalArgumentException validationException) {
            throw validationException;
        } catch (Exception primaryException) {
            log.warn("Primary template generation failed for cluster {}: {}. Falling back to raw manifests.",
                    id, primaryException.getMessage());
            return createTemplateFromRawManifests(id, clusterDTO, primaryException);
        }
    }


    private List<NodeDTO> orderNodesAncestorsFirst(ClusterDTO clusterDTO) {
        Map<String, NodeDTO> nodesById = clusterDTO.getNodes().stream()
                .collect(Collectors.toMap(NodeDTO::getId, node -> node, (a, b) -> a, LinkedHashMap::new));

        Map<String, Integer> indegree = new LinkedHashMap<>();
        nodesById.keySet().forEach(id -> indegree.put(id, 0));

        Map<String, List<String>> graph = new LinkedHashMap<>();
        nodesById.keySet().forEach(id -> graph.put(id, new ArrayList<>()));

        for (LinkDTO link : clusterDTO.getLinks()) {
            if (!nodesById.containsKey(link.getSource()) || !nodesById.containsKey(link.getTarget())) {
                continue;
            }
            graph.get(link.getSource()).add(link.getTarget());
            indegree.put(link.getTarget(), indegree.getOrDefault(link.getTarget(), 0) + 1);
        }

        PriorityQueue<String> queue = new PriorityQueue<>((a, b) -> {
            NodeDTO na = nodesById.get(a);
            NodeDTO nb = nodesById.get(b);
            int cmp = Optional.ofNullable(na.getName()).orElse("").compareToIgnoreCase(Optional.ofNullable(nb.getName()).orElse(""));
            return cmp != 0 ? cmp : a.compareToIgnoreCase(b);
        });
        indegree.forEach((id, deg) -> {
            if (deg == 0) {
                queue.add(id);
            }
        });

        List<NodeDTO> ordered = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            ordered.add(nodesById.get(id));
            for (String neighbor : graph.get(id)) {
                indegree.put(neighbor, indegree.get(neighbor) - 1);
                if (indegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (ordered.size() != nodesById.size()) {
            throw new IllegalStateException("Circular dependency detected in cluster graph");
        }

        return ordered;
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

    private void enforceServiceAccountConstraints(Map<String, NodeDTO> nodesById, String namespace) {
        if (nodesById == null || namespace == null || namespace.isBlank()) {
            return;
        }

        Map<String, String> seenNames = new LinkedHashMap<>();
        for (NodeDTO node : nodesById.values()) {
            if (!(node instanceof ServiceAccountDTO sa)) {
                continue;
            }
            String saNamespace = sa.getNamespace();
            if (saNamespace != null && !saNamespace.isBlank() && !namespace.equals(saNamespace)) {
                throw new IllegalArgumentException("Workload namespace must match ServiceAccount namespace. Kubernetes does not allow using a ServiceAccount across namespaces.");
            }
            String name = sa.getName() == null ? "" : sa.getName().trim();
            if (name.isBlank()) {
                throw new IllegalArgumentException("ServiceAccount name is required");
            }
            String existingId = seenNames.putIfAbsent(name, sa.getId());
            if (existingId != null && !existingId.equals(sa.getId())) {
                throw new IllegalArgumentException("Duplicate ServiceAccount name '" + name + "' in namespace '" + namespace + "'");
            }
        }
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
