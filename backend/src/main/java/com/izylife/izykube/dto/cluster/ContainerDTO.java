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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ContainerDTO extends NodeDTO {
    private String assetId;
    private int containerPort;
    private ContainerRole role;

    public ContainerDTO(String id, String name, String assetId, int containerPort) {
        this(id, name, assetId, containerPort, null);
    }

    public ContainerDTO(String id, String name, String assetId, int containerPort, ContainerRole role) {
        super(id, name, "container");
        this.assetId = assetId;
        this.containerPort = containerPort;
        this.role = role;
    }
}
