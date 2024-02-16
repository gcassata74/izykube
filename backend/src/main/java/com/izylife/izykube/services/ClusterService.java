package com.izylife.izykube.services;

import com.izylife.izykube.dto.cluster.Cluster;
import com.izylife.izykube.dto.cluster.Container;
import com.izylife.izykube.dto.cluster.PodSpec;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import com.izylife.izykube.services.k8s.K8sPodService;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;


@Service
@Slf4j
public class ClusterService {

    @Autowired
    private K8sPodService k8sPodService;

    @Autowired
    private AssetRepository assetRepository;

    public void createCluster(Cluster cluster) throws KubernetesClientException {

        //containers can be more than one (app, init, sidecar)
        Container[] container = cluster.getNodes().stream().filter(node -> node.getKind().equals("Container")).map(node -> (Container) node).toArray(Container[]::new);

        // get the container0 and the asset from the repository
        Container container0 = container[0];
        Asset asset = assetRepository.findById(container0.getAssetId()).orElse(null);
        PodSpec podSpec = cluster.getNodes().stream().filter(node -> node.getKind().equals("Pod")).map(node -> (PodSpec) node).findFirst().orElse(null);

        // create the pod
        //TODO: consider the namespace use default at the moment
        k8sPodService.createPod(podSpec.getName(), asset.getImage(), "default", new HashMap<>() {{
            put("app", asset.getName());
            put("version", asset.getVersion());
        }}, asset.getPort());

        log.info("Pod created successfully");
    }
}
