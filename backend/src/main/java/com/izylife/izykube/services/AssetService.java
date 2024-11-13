package com.izylife.izykube.services;

import com.izylife.izykube.dto.cluster.AssetDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Slf4j
public class AssetService {

    @Value("${app.docker.local-repository-uri}")
    private String localRepositoryUri;
    @Autowired
    private AssetRepository assetRepository;


    public List<AssetDTO> findAll() {
        List<Asset> assets = assetRepository.findAll();
        return assets.stream().map(asset -> {
            AssetDTO assetDTO = new AssetDTO();
            assetDTO.setId(asset.getId());
            assetDTO.setName(asset.getName());
            assetDTO.setType(asset.getType());
            assetDTO.setDescription(asset.getDescription());
            assetDTO.setImage(asset.getImage());
            assetDTO.setVersion(asset.getVersion());
            return assetDTO;
        }).toList();
    }

    public Asset getAsset(String assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new NoSuchElementException(String.format("Asset not found %s", assetId)));
    }

    public void deleteAsset(String id) {
        try {
            assetRepository.deleteById(id);
        } catch (Exception e) {
            log.error("Error deleting asset: " + e.getMessage());
        }
    }

    public Asset updateAsset(String id, Asset asset) {
        Asset existingAsset = assetRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(String.format("Asset not found %s", id)));
        existingAsset.setName(asset.getName());
        existingAsset.setDescription(asset.getDescription());
        existingAsset.setImage(asset.getImage());
        existingAsset.setVersion(asset.getVersion());
        return assetRepository.save(existingAsset);
    }

    public Asset createAsset(Asset asset) {
        return assetRepository.save(asset);
    }
}
