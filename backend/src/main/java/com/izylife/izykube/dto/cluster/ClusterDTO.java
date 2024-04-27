package com.izylife.izykube.dto.cluster;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class ClusterDTO {
    private String id;
    private String name;
    private String nameSpace;
    private List<NodeDTO> nodes = new ArrayList<>();
    private List<LinkDTO> links = new ArrayList<>();
    private String diagram;

    // Find node by ID
    public NodeDTO findNodeById(String id) {
        return nodes.stream()
                .filter(node -> node.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    // Find nodes by kind
    public List<NodeDTO> findNodesByKind(String kind) {
        return nodes.stream()
                .filter(node -> node.getKind().equalsIgnoreCase(kind))
                .collect(Collectors.toList());
    }

    // Find all links that have the given node ID as target
    public List<LinkDTO> findLinksByTarget(String targetId) {
        return links.stream()
                .filter(link -> link.getTarget().equals(targetId))
                .collect(Collectors.toList());
    }

    // find all the nodes that are sources of a specific node
    public List<NodeDTO> findSourceNodesOf(String targetId) {
        List<String> sourceIds = links.stream()
                .filter(link -> link.getTarget().equals(targetId))
                .map(LinkDTO::getSource)
                .collect(Collectors.toList());

        return nodes.stream()
                .filter(node -> sourceIds.contains(node.getId()))
                .collect(Collectors.toList());
    }

    // Find all nodes that are targets of a specific node
    public List<NodeDTO> findTargetNodesOf(String sourceId) {
        List<String> targetIds = links.stream()
                .filter(link -> link.getSource().equals(sourceId))
                .map(LinkDTO::getTarget)
                .collect(Collectors.toList());

        return nodes.stream()
                .filter(node -> targetIds.contains(node.getId()))
                .collect(Collectors.toList());
    }
}
