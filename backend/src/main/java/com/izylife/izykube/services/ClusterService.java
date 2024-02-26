package com.izylife.izykube.services;

import com.izylife.izykube.dto.cluster.*;
import com.izylife.izykube.repositories.AssetRepository;
import com.izylife.izykube.services.k8s.K8sDeploymentService;
import com.izylife.izykube.services.k8s.K8sPodService;
import com.izylife.izykube.services.k8s.K8sServiceService;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Slf4j
public class ClusterService {

    @Autowired
    private K8sPodService k8sPodService;

    @Autowired
    private K8sServiceService k8sServiceService;

    @Autowired
    private K8sDeploymentService k8sDeploymentService;

    @Autowired
    private AssetRepository assetRepository;


    public void createCluster(Cluster cluster) throws KubernetesClientException {
        // Iterate over each node in the cluster
        cluster.getNodes().stream().filter(node -> node.getKind().equals("pod") || node.getKind().equals("deployment")).forEach(node -> {
            if (node.getKind().equals("pod")) {
                handlePod(cluster, (Pod) node);
            } else {
                handleDeployment(cluster, (Deployment) node);
            }
        });
    }

    private void handlePod(Cluster cluster, Pod pod) {
        k8sPodService.createPod(pod);
        // Handle connections from this Pod to other resources
    }

    private void handleDeployment(Cluster cluster, Deployment deployment) {
        // Find all config maps or other resources connected to this deployment
        List<Node> sourceNodes = cluster.findSourceNodesOf(deployment.getId());
        for (Node sourceNode : sourceNodes) {
            // Process source nodes, e.g., ConfigMaps
            if (sourceNode instanceof ConfigMap) {
                k8sDeploymentService.addConfigMap((ConfigMap) sourceNode);
            }
        }
        k8sDeploymentService.createDeployment(deployment);
    }


}
