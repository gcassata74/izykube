package com.izylife.izykube.services;

import com.izylife.izykube.dto.cluster.AssetDTO;
import com.izylife.izykube.enums.AssetSource;
import com.izylife.izykube.enums.AssetType;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;

    private AssetService assetService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        assetService = new AssetService(assetRepository);
    }

    @Test
    void findImageAssetsFiltersBySearchTerm() {
        Asset nginx = imageAsset("nginx", "nginx:latest");
        Asset redis = imageAsset("redis", "redis:7");
        when(assetRepository.findByType(AssetType.IMAGE)).thenReturn(List.of(nginx, redis));

        List<AssetDTO> results = assetService.findImageAssets("redis");

        assertEquals(1, results.size());
        assertEquals("redis", results.get(0).getName());
    }

    @Test
    void createImageAssetReturnsExistingWhenDuplicate() {
        Asset existing = imageAsset("nginx", "nginx:latest");
        when(assetRepository.findByTypeAndImageIgnoreCase(eq(AssetType.IMAGE), eq("nginx:latest")))
                .thenReturn(Optional.of(existing));

        AssetDTO dto = assetService.createImageAsset("nginx", "nginx:latest", null);

        verify(assetRepository, never()).save(any());
        assertEquals("nginx", dto.getName());
        assertEquals("nginx:latest", dto.getImage());
    }

    @Test
    void createImageAssetPersistsNewEntry() {
        when(assetRepository.findByTypeAndImageIgnoreCase(eq(AssetType.IMAGE), eq("registry/app:v1")))
                .thenReturn(Optional.empty());
        when(assetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AssetDTO dto = assetService.createImageAsset("My App", "registry/app:v1", "Test image");

        verify(assetRepository).save(any(Asset.class));
        assertEquals("registry/app:v1", dto.getImage());
        assertEquals("v1", dto.getVersion());
    }

    private Asset imageAsset(String name, String image) {
        Asset asset = new Asset();
        asset.setName(name);
        asset.setType(AssetType.IMAGE);
        asset.setImage(image);
        asset.setVersion("latest");
        asset.setSource(AssetSource.USER);
        return asset;
    }
}
