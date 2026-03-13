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
import lombok.Setter;

@Getter
@Setter
public class JobDTO extends NodeDTO {


    private String assetId;
    private String serviceAccountRef;
    private String serviceAccountName;

    @JsonCreator
    public JobDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("assetId") String assetId,
            @JsonProperty("serviceAccountRef") String serviceAccountRef,
            @JsonProperty("serviceAccountName") String serviceAccountName
    ) {
        super(id, name, "job");
        this.assetId = assetId;
        this.serviceAccountRef = serviceAccountRef;
        this.serviceAccountName = serviceAccountName;
    }

    public JobDTO(String id, String name, String assetId) {
        this(id, name, assetId, null, null);
    }

    public JobDTO(String id, String name, String assetId, String serviceAccountRef) {
        this(id, name, assetId, serviceAccountRef, null);
    }
}
