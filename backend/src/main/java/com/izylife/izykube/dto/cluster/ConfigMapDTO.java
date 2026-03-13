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

package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ConfigMapDTO extends NodeDTO {

    private String yaml;
    /**
     * Legacy override to treat the whole resource as a Secret when no per-entry sensitivity is available.
     * Ignored when entries are provided.
     */
    private List<ConfigEntryDTO> entries = new ArrayList<>();
    private Boolean showSecretsAsPlain;

    public ConfigMapDTO(String id, String name, String yaml) {
        this(id, name, yaml, false, "configmap");
    }

    @JsonCreator
    public ConfigMapDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("yaml") String yaml,
            @JsonProperty("secret") Boolean secret
    ) {
        this(id, name, yaml, Boolean.TRUE.equals(secret), "configmap");
    }

    protected ConfigMapDTO(String id, String name, String yaml, boolean secret, String kind) {
        super(id, name, kind);
        this.yaml = yaml;
    }

    public List<ConfigEntryDTO> getEntries() {
        if (entries == null) {
            entries = new ArrayList<>();
        }
        return entries;
    }

    public void setEntries(List<ConfigEntryDTO> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    @Override
    public void setKind(String kind) {
        super.setKind(kind == null ? "configmap" : kind);
    }
}
