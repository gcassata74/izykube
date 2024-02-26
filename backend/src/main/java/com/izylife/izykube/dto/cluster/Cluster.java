package com.izylife.izykube.dto.cluster;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class Cluster {
    private String id;
    private String name;
    private String nameSpace = "default"; //default namespace is "default
    private List<? extends Node> nodes;
    private List<Link> links;
    private String diagram;
    private Date creationDate;
    private Date lastUpdated;

    // Find node by ID
    public Node findNodeById(String id) {
        return nodes.stream()
                .filter(node -> node.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    // Find nodes by kind
    public List<Node> findNodesByKind(String kind) {
        return nodes.stream()
                .filter(node -> node.getKind().equalsIgnoreCase(kind))
                .collect(Collectors.toList());
    }

    // Find all links that have the given node ID as target
    public List<Link> findLinksByTarget(String targetId) {
        return links.stream()
                .filter(link -> link.getTarget().equals(targetId))
                .collect(Collectors.toList());
    }

    // find all the nodes that are sources of a specific node
    public List<Node> findSourceNodesOf(String targetId) {
        List<String> sourceIds = links.stream()
                .filter(link -> link.getTarget().equals(targetId))
                .map(Link::getSource)
                .collect(Collectors.toList());

        return nodes.stream()
                .filter(node -> sourceIds.contains(node.getId()))
                .collect(Collectors.toList());
    }

    // Find all nodes that are targets of a specific node
    public List<Node> findTargetNodesOf(String sourceId) {
        List<String> targetIds = links.stream()
                .filter(link -> link.getSource().equals(sourceId))
                .map(Link::getTarget)
                .collect(Collectors.toList());

        return nodes.stream()
                .filter(node -> targetIds.contains(node.getId()))
                .collect(Collectors.toList());
    }
}
