package com.izylife.izykube.factory;

import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.dto.cluster.PodDTO;


public class NodeFactory {

    public static NodeDTO createNodeDTO(NodeDTO node) {
        switch (node.getKind()) {
            case "configmap":
                ConfigMapDTO configMap = (ConfigMapDTO) node;
                return new ConfigMapDTO(configMap.getId(), configMap.getName(), configMap.getEntries());
            case "pod":
                PodDTO pod = (PodDTO) node;
                return new PodDTO(pod.getId(), pod.getName(), pod.getAssetId());
            // Add cases for other node types as needed
            default:
                return null;
        }
    }

}
