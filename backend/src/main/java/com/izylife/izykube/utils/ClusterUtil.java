package com.izylife.izykube.utils;

import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;

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
}