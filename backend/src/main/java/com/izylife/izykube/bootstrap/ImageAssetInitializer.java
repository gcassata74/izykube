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

import com.izylife.izykube.enums.AssetSource;
import com.izylife.izykube.enums.AssetType;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import com.izylife.izykube.configuration.ImageAssetProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageAssetInitializer implements ApplicationRunner {

    private final AssetRepository assetRepository;
    private final ImageAssetProperties imageAssetProperties;

    @Override
    public void run(ApplicationArguments args) {
        imageAssetProperties.getDefaultImages().forEach(this::upsertImageAsset);
    }

    private void upsertImageAsset(String imageRef) {
        boolean exists = assetRepository.existsByTypeAndImageIgnoreCase(AssetType.IMAGE, imageRef);
        if (exists) {
            return;
        }

        Asset asset = new Asset();
        asset.setName(imageRef);
        asset.setType(AssetType.IMAGE);
        asset.setImage(imageRef);
        asset.setVersion(extractTag(imageRef));
        asset.setSource(AssetSource.BUILT_IN);
        assetRepository.save(asset);
        log.info("Registered built-in image asset {}", imageRef);
    }

    private String extractTag(String imageRef) {
        int idx = imageRef.lastIndexOf(':');
        if (idx == -1 || idx == imageRef.length() - 1) {
            return "latest";
        }
        return imageRef.substring(idx + 1);
    }
}
