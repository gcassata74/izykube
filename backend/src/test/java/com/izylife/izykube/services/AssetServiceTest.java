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
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void findControllerAssetsReturnsOnlyControllerAssets() {
        Asset script = new Asset();
        script.setId("a1");
        script.setName("script");
        script.setType(AssetType.SCRIPT);

        Asset playbook = new Asset();
        playbook.setId("a2");
        playbook.setName("playbook");
        playbook.setType(AssetType.PLAYBOOK);

        Asset jva = new Asset();
        jva.setId("a3");
        jva.setName("jva");
        jva.setType(AssetType.JVA);

        Asset image = imageAsset("nginx", "nginx:latest");
        image.setId("a4");

        Asset controller = new Asset();
        controller.setId("a5");
        controller.setName("controller");
        controller.setType(AssetType.CONTROLLER);

        when(assetRepository.findByType(AssetType.CONTROLLER)).thenReturn(List.of(controller));

        List<AssetDTO> results = assetService.findControllerAssets();

        assertEquals(1, results.size());
        assertTrue(results.stream().anyMatch(a -> "a5".equals(a.getId())));
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
