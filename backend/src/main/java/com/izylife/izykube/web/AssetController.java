package com.izylife.izykube.web;

import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assets") // Base URL per tutti gli endpoint in questo controller
public class AssetController {

    private final AssetRepository assetRepository;

    @Autowired
    public AssetController(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @GetMapping("/all")
    public List<Asset> getAllAssets() {
        return assetRepository.findAll();
    }
}
