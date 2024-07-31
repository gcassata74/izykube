package com.izylife.izykube.web;


import com.izylife.izykube.dto.GenericResponseDTO;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.services.ClusterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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

    @GetMapping("/all")
    public ResponseEntity<?> getAllClusters() {
        try {
            return ResponseEntity.ok(clusterService.getAllClusters());
        } catch (Exception e) {
            log.error("Error getting all clusters: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error getting all clusters: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getClusterById(@PathVariable String id) {
        try {

            return ResponseEntity.ok(clusterService.getClusterById(id));
        } catch (Exception e) {
            log.error("Error getting cluster with ID " + id + ": " + e.getMessage());
            return ResponseEntity.badRequest().body("Error getting cluster with ID " + id + ": " + e.getMessage());
        }
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCluster(@PathVariable String id) {
        try {
            clusterService.deleteCluster(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Cluster deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting cluster: " + e.getMessage());
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error deleting cluster: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/{id}/template")
    @ResponseBody
    public ResponseEntity<GenericResponseDTO> createTemplate(@PathVariable String id) {
        GenericResponseDTO response = new GenericResponseDTO();
        try {
            clusterService.createTemplate(id);
            response.setMessage("The template was created successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setError("The template could not be created");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{id}/template")
    @ResponseBody
    public ResponseEntity<GenericResponseDTO> deleteTemplate(@PathVariable String id) {
        GenericResponseDTO response = new GenericResponseDTO();
        try {
            clusterService.deleteTemplate(id);
            response.setMessage("The template was created successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setError("The template could not be created");
            return ResponseEntity.badRequest().body(response);
        }
    }



    @PostMapping("/{id}/deploy")
    public ResponseEntity<GenericResponseDTO> deploy(@PathVariable String id) {
        GenericResponseDTO response = new GenericResponseDTO();
        try {
            clusterService.deploy(id);
            response.setMessage("The deployment was successful");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setError("The deployment could not be completed");
            return ResponseEntity.badRequest().body(response);
        }
    }


    @DeleteMapping("/{id}/undeploy")
    public ResponseEntity<GenericResponseDTO> undeploy(@PathVariable String id) {
        GenericResponseDTO response = new GenericResponseDTO();
        try {
            clusterService.undeploy(id);
            response.setMessage("The cluster has been successfully undeployed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setError("The cluster could not be undeployed");
            return ResponseEntity.badRequest().body(response);
        }
    }



}
