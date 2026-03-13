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

import com.izylife.izykube.dto.storage.PersistentVolumeDTO;
import com.izylife.izykube.services.PersistentVolumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/persistent-volumes")
@RequiredArgsConstructor
@Slf4j
public class PersistentVolumeController {

    private final PersistentVolumeService persistentVolumeService;

    @GetMapping
    public List<PersistentVolumeDTO> list() {
        return persistentVolumeService.listPersistentVolumes();
    }

    @GetMapping("/{name}")
    public ResponseEntity<PersistentVolumeDTO> get(@PathVariable String name) {
        PersistentVolumeDTO dto = persistentVolumeService.getPersistentVolume(name);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<PersistentVolumeDTO> create(@RequestBody PersistentVolumeDTO request) {
        try {
            return ResponseEntity.ok(persistentVolumeService.createOrUpdate(request));
        } catch (Exception e) {
            log.error("Unable to create persistent volume {}: {}", request != null ? request.getName() : "unknown", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{name}")
    public ResponseEntity<PersistentVolumeDTO> update(@PathVariable String name, @RequestBody PersistentVolumeDTO request) {
        if (request != null) {
            request.setName(name);
        }
        try {
            return ResponseEntity.ok(persistentVolumeService.createOrUpdate(request));
        } catch (Exception e) {
            log.error("Unable to update persistent volume {}: {}", name, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        try {
            boolean deleted = persistentVolumeService.deletePersistentVolume(name);
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Unable to delete persistent volume {}: {}", name, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
