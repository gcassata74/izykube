package com.izylife.izykube.web;

import com.izylife.izykube.dto.cluster.AssetDTO;
import com.izylife.izykube.services.AssetService;
import com.izylife.izykube.web.request.ImageAssetRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/image-assets")
@RequiredArgsConstructor
@Slf4j
public class ImageAssetController {

    private final AssetService assetService;

    @GetMapping
    public List<AssetDTO> listImageAssets(@RequestParam(value = "search", required = false) String search) {
        return assetService.findImageAssets(search);
    }

    @PostMapping
    public ResponseEntity<?> createImageAsset(@Valid @RequestBody ImageAssetRequest request) {
        try {
            AssetDTO dto = assetService.createImageAsset(request.getName(), request.getImageRef(), request.getDescription());
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException ex) {
            log.error("Unable to create image asset: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}
