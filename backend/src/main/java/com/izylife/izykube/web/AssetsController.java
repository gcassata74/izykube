package com.izylife.izykube.web;

import com.izylife.izykube.dto.cluster.AssetDTO;
import com.izylife.izykube.services.AssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assets")
public class AssetsController {

    private final AssetService assetService;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(name = "type", required = false) String type) {
        try {
            if ("controller".equalsIgnoreCase(type)) {
                return ResponseEntity.ok(assetService.findControllerAssets());
            }
            List<AssetDTO> assets = assetService.findAll();
            return ResponseEntity.ok(assets);
        } catch (Exception e) {
            log.error("Error listing assets: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error listing assets: " + e.getMessage());
        }
    }
}

