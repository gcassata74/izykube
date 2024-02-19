package com.izylife.izykube.services;

import com.izylife.izykube.dto.cluster.Cluster;
import com.izylife.izykube.dto.cluster.Node;
import com.izylife.izykube.dto.cluster.Pod;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import com.izylife.izykube.services.k8s.K8sDeploymentService;
import com.izylife.izykube.services.k8s.K8sPodService;
import com.izylife.izykube.services.k8s.K8sServiceService;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;


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

        // find all the pods in the cluster and create them in k8s
        List<Pod> pods = cluster.getNodes().stream()
                .filter(node -> "pod".equals(node.getKind()))
                .map(Pod.class::cast)
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new NoSuchElementException("Mo pods found in cluster"));

        pods.forEach((pod) -> {
            Asset asset = Optional.ofNullable(pod.getAssetId())
                    .flatMap(assetRepository::findById)
                    .orElseThrow(() -> new NoSuchElementException("Asset not found with id: " + pod.getAssetId()));

//            k8sPodService.createPod(pod.getName(), asset.getImage(), "default", new HashMap<>() {{
//                put("app", asset.getName());
//                put("version", asset.getVersion());
//            }}, asset.getPort());

            // get all the links that have the pod as target
            cluster.findLinksByTarget(pod.getId()).forEach(link -> {
                // find the source node of the link
                Node source = (Node) cluster.findNodeById(link.getSource());
                // create the k8s objects based on thei kind
                if ("service".equals(source.getKind())) {


                }
            });


        });



        log.info("Pod created successfully");
    }
}
