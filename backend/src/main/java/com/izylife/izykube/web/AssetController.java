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

package com.izylife.izykube.web;

import com.izylife.izykube.dto.cluster.AssetDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.services.AssetService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/asset")
public class AssetController {

    private AssetService assetService;

    @GetMapping("/all")
    public List<AssetDTO> getAllAssets() {
        return assetService.findAll();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCluster(@PathVariable String id) {
        try {
            assetService.deleteAsset(id);
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

    @GetMapping("/{id}")
    public ResponseEntity<?> getAsset(@PathVariable String id) {
        try {
            Asset asset = assetService.getAsset(id);
            return ResponseEntity.ok(asset);
        } catch (Exception e) {
            log.error("Error getting asset: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error getting asset: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createAsset(@RequestBody Asset asset) {
        try {
            Asset savedAsset = assetService.createAsset(asset);
            return ResponseEntity.ok(savedAsset);
        } catch (Exception e) {
            log.error("Error saving asset: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error saving asset: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAsset(@PathVariable String id, @RequestBody Asset asset) {
        try {
            Asset updatedAsset = assetService.updateAsset(id, asset);
            return ResponseEntity.ok(updatedAsset);
        } catch (Exception e) {
            log.error("Error updating asset: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error updating asset: " + e.getMessage());
        }
    }

}
