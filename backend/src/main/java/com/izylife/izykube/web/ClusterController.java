package com.izylife.izykube.web;


import com.izylife.izykube.dto.cluster.Cluster;
import com.izylife.izykube.services.ClusterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cluster")
public class ClusterController {
    @Autowired
    private ClusterService clusterService;

    @PostMapping
    public ResponseEntity<?> createCluster(@RequestBody Cluster cluster) {
        try {
            clusterService.createCluster(cluster);
            return ResponseEntity.ok().body("Cluster created successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error creating cluster: " + e.getMessage());
        }
    }

}
