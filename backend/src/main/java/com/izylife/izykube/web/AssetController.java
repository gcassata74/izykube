package com.izylife.izykube.web;

import com.izylife.izykube.dto.cluster.AssetDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import com.izylife.izykube.services.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/asset")
public class AssetController {

    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private AssetService assetService;


    @Autowired
    public AssetController(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @GetMapping("/all")
    public List<Asset> getAllAssets() {
        return assetRepository.findAll();
    }

    @PostMapping()
    @Operation(summary = "Create or Update an Asset",
            description = "Creates a new asset or updates an existing one. If a Dockerfile is provided, a new image will be built.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Asset created/updated successfully",
                            content = @Content(schema = @Schema(implementation = Asset.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "500", description = "Server error")
            })
    public ResponseEntity<Asset> createOrUpdateAsset(@RequestBody AssetDTO assetDTO) throws Exception {
        Asset asset = new Asset();
        asset.setName(assetDTO.getName());
        asset.setVersion(assetDTO.getVersion());
        asset.setPort(assetDTO.getPort());
        asset.setDescription(assetDTO.getDescription());
        asset.setImage(assetDTO.getImage());
        Asset savedAsset = assetService.createAsset(asset);
        return ResponseEntity.ok(savedAsset);
    }

}
