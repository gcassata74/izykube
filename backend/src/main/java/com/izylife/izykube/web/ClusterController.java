package com.izylife.izykube.web;


import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.services.ClusterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequestMapping("/api/cluster")
public class ClusterController {
    @Autowired
    private ClusterService clusterService;


    @PostMapping
    public ResponseEntity<?> createCluster(@RequestBody ClusterDTO clusterDTO) {
        try {
            ClusterDTO savedCluster = clusterService.createCluster(clusterDTO);
            // Check if the cluster was saved successfully (i.e., not null)
            if (savedCluster != null && savedCluster.getId() != null) {
                return ResponseEntity.ok(savedCluster); // Returns the saved cluster with the id
            } else {
                // Handle the case where the cluster wasn't saved properly
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving cluster");
            }
        } catch (Exception e) {
            log.error("Error saving cluster: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error saving cluster: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCluster(@PathVariable String id, @RequestBody ClusterDTO clusterDTO) {
        try {
            clusterDTO.setId(id); // Ensure the DTO has the correct ID, in case it's not included in the body
            ClusterDTO updatedCluster = clusterService.updateCluster(clusterDTO);

            // Check if the cluster was updated successfully
            if (updatedCluster != null) {
                return ResponseEntity.ok(updatedCluster); // Returns the updated cluster
            } else {
                // Handle the case where the cluster wasn't updated properly
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating cluster");
            }
        } catch (Exception e) {
            log.error("Error updating cluster: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error updating cluster: " + e.getMessage());
        }
    }



}
