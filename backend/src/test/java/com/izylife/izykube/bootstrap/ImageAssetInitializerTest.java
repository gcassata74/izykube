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

package com.izylife.izykube.bootstrap;

import com.izylife.izykube.configuration.ImageAssetProperties;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.ApplicationArguments;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageAssetInitializerTest {

    @Mock
    private AssetRepository assetRepository;

    private ImageAssetProperties properties;
    private ImageAssetInitializer initializer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new ImageAssetProperties();
        properties.setDefaultImages(List.of("nginx:latest", "redis:7"));
        initializer = new ImageAssetInitializer(assetRepository, properties);
    }

    @Test
    void seedsDefaultImagesWhenMissing() throws Exception {
        when(assetRepository.existsByTypeAndImageIgnoreCase(any(), any())).thenReturn(false);

        initializer.run(mock(ApplicationArguments.class));

        verify(assetRepository, times(properties.getDefaultImages().size())).save(any(Asset.class));
    }

    @Test
    void skipsCreationWhenImageAlreadyPresent() throws Exception {
        when(assetRepository.existsByTypeAndImageIgnoreCase(any(), any())).thenReturn(true);

        initializer.run(mock(ApplicationArguments.class));

        verify(assetRepository, never()).save(any());
    }
}
