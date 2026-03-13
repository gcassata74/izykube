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

import com.izylife.izykube.dto.GenericResponseDTO;
import com.izylife.izykube.dto.cluster.ClusterDTO;
import com.izylife.izykube.services.TemplateService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequestMapping("/api/template")
@AllArgsConstructor
public class TemplateController {

    private TemplateService templateService;

    @PostMapping("/{id}")
    @ResponseBody
    public ResponseEntity<GenericResponseDTO> createTemplate(@PathVariable String id) {
        GenericResponseDTO response = new GenericResponseDTO();
        try {
            templateService.createTemplate(id);
            response.setMessage("The template was created successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setError(e instanceof IllegalArgumentException ? e.getMessage() : "The template could not be created");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<GenericResponseDTO> updateTemplate(@PathVariable String id, @RequestBody ClusterDTO clusterDTO) {
        GenericResponseDTO response = new GenericResponseDTO();
        try {
            templateService.updateTemplate(id, clusterDTO);
            response.setMessage("The template was created successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setError(e instanceof IllegalArgumentException ? e.getMessage() : "The template could not be created");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<GenericResponseDTO> deleteTemplate(@PathVariable String id) {
        GenericResponseDTO response = new GenericResponseDTO();
        try {
            templateService.deleteTemplate(id);
            response.setMessage("The template was created successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.setError("The template could not be created");
            return ResponseEntity.badRequest().body(response);
        }
    }

}
