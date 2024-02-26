package com.izylife.izykube.services;

import com.github.dockerjava.api.DockerClient;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@Slf4j
public class AssetService {

    @Value("${app.docker.local-repository-uri}")
    private String localRepositoryUri;
    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private DockerClient dockerClient;


    public Asset getAsset(String assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new NoSuchElementException("Asset not found with id: " + assetId));
    }

    public Asset createAsset(Asset asset) throws Exception {
        return assetRepository.save(asset);
    }


}
