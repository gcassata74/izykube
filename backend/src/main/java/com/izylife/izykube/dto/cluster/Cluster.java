package com.izylife.izykube.dto.cluster;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data

public class Cluster {
    private String id;
    private String name;
    private List<? extends Node> nodes;
    private List<Link> links;
    private String diagram;
    private Date creationDate;
    private Date lastUpdated;

    public Node findNodeById(String id) {
        for (Node node : nodes) {
            if (node.getId().equals(id)) {
                return node;
            }
        }
        return null;
    }

    //find all thw links that have the given node id as target
    public List<Link> findLinksByTarget(String targetId) {
        return links.stream().filter(link -> link.getTarget().equals(targetId)).collect(Collectors.toList());
    }



}
