package com.izylife.izykube.utils;

import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.model.Cluster;

import java.util.List;
import java.util.stream.Collectors;

public class ClusterUtil {

    // Find node by ID
    public static NodeDTO findNodeById(ClusterDTO cluster, String id) {
        return cluster.getNodes().stream()
                .filter(node -> node.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    // Find nodes by kind
    public static List<NodeDTO> findNodesByKind(ClusterDTO cluster, String kind) {
        return cluster.getNodes().stream()
                .filter(node -> node.getKind().equalsIgnoreCase(kind))
                .collect(Collectors.toList());
    }

    // Find all links that have the given node ID as target
    public static List<LinkDTO> findLinksByTarget(ClusterDTO cluster, String targetId) {
        return cluster.getLinks().stream()
                .filter(link -> link.getTarget().equals(targetId))
                .collect(Collectors.toList());
    }

    // Find all the nodes that are sources of a specific node
    public static List<NodeDTO> findSourceNodesOf(ClusterDTO cluster, String targetId) {
        List<String> sourceIds = cluster.getLinks().stream()
                .filter(link -> link.getTarget().equals(targetId))
                .map(LinkDTO::getSource)
                .collect(Collectors.toList());

        return cluster.getNodes().stream()
                .filter(node -> sourceIds.contains(node.getId()))
                .collect(Collectors.toList());
    }

    // Find all nodes that are targets of a specific node
    public static List<NodeDTO> findTargetNodesOf(ClusterDTO cluster, String sourceId) {
        List<String> targetIds = cluster.getLinks().stream()
                .filter(link -> link.getSource().equals(sourceId))
                .map(LinkDTO::getTarget)
                .collect(Collectors.toList());

        return cluster.getNodes().stream()
                .filter(node -> targetIds.contains(node.getId()))
                .collect(Collectors.toList());
    }

    public static ClusterDTO convertToDTO(Cluster cluster) {
        return ClusterDTO.builder()
                .id(cluster.getId())
                .name(cluster.getName())
                .nameSpace(cluster.getNameSpace())
                .nodes(cluster.getNodes())
                .links(cluster.getLinks())
                .diagram(cluster.getDiagram())
                .status(cluster.getStatus())
                .build();
    }

    // Convert ClusterDTO to Cluster
    public static Cluster convertToEntity(ClusterDTO clusterDTO) {
        Cluster cluster = new Cluster();
        cluster.setId(clusterDTO.getId());
        cluster.setName(clusterDTO.getName());
        cluster.setNameSpace(clusterDTO.getNameSpace());
        cluster.setNodes(clusterDTO.getNodes());
        cluster.setLinks(clusterDTO.getLinks());
        cluster.setDiagram(clusterDTO.getDiagram());
        cluster.setStatus(clusterDTO.getStatus());
        return cluster;
    }

}