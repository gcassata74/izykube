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

