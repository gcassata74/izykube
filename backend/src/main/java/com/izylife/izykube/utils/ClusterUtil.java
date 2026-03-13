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
        if (cluster == null || cluster.getLinks() == null) {
            return List.of();
        }
        return cluster.getLinks().stream()
                .filter(link -> targetId != null && targetId.equals(link.getTarget()))
                .collect(Collectors.toList());
    }

    // Find all the nodes that are sources of a specific node
    public static List<NodeDTO> findSourceNodesOf(ClusterDTO cluster, String targetId) {
        if (cluster == null || cluster.getLinks() == null) {
            return List.of();
        }
        List<String> sourceIds = cluster.getLinks().stream()
                .filter(link -> targetId != null && targetId.equals(link.getTarget()))
                .map(LinkDTO::getSource)
                .collect(Collectors.toList());

        return cluster.getNodes().stream()
                .filter(node -> sourceIds.contains(node.getId()))
                .collect(Collectors.toList());
    }

    // Find all nodes that are targets of a specific node
    public static List<NodeDTO> findTargetNodesOf(ClusterDTO cluster, String sourceId) {
        if (cluster == null || cluster.getLinks() == null) {
            return List.of();
        }
        List<String> targetIds = cluster.getLinks().stream()
                .filter(link -> sourceId != null && sourceId.equals(link.getSource()))
                .map(LinkDTO::getTarget)
                .collect(Collectors.toList());

        return cluster.getNodes().stream()
                .filter(node -> targetIds.contains(node.getId()))
                .collect(Collectors.toList());
    }

    //get all the ancesoors of a node
    public static List<NodeDTO> getAncestorsOfNode(ClusterDTO cluster, NodeDTO node) {
        List<NodeDTO> ancestors = findSourceNodesOf(cluster, node.getId());
        for (NodeDTO ancestor : ancestors) {
            ancestors.addAll(getAncestorsOfNode(cluster, ancestor));
        }
        return ancestors;
    }

    // get all the descendants of a node
    public static List<NodeDTO> getDescendantsOfNode(ClusterDTO cluster, NodeDTO node) {
        List<NodeDTO> descendants = findTargetNodesOf(cluster, node.getId());
        for (NodeDTO descendant : descendants) {
            descendants.addAll(getDescendantsOfNode(cluster, descendant));
        }
        return descendants;
    }

    // get a node by type in the ancestors of a node
    public static NodeDTO getAncestorByType(ClusterDTO cluster, NodeDTO node, String type) {
        List<NodeDTO> ancestors = getAncestorsOfNode(cluster, node);
        return ancestors.stream()
                .filter(ancestor -> ancestor.getKind().equalsIgnoreCase(type))
                .findFirst()
                .orElse(null);
    }

    public static List<LinkDTO> findLinksBySource(ClusterDTO cluster, String sourceId) {
        if (cluster == null || cluster.getLinks() == null) {
            return List.of();
        }
        return cluster.getLinks().stream()
                .filter(link -> sourceId != null && sourceId.equals(link.getSource()))
                .collect(Collectors.toList());
    }

    // get a node by type in the descendants of a node
    public static NodeDTO getDescendantByType(ClusterDTO cluster, NodeDTO node, String type) {
        List<NodeDTO> descendants = getDescendantsOfNode(cluster, node);
        return descendants.stream()
                .filter(descendant -> descendant.getKind().equalsIgnoreCase(type))
                .findFirst()
                .orElse(null);
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
