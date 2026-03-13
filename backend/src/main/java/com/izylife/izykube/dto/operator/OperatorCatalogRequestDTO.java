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

package com.izylife.izykube.dto.operator;

import com.izylife.izykube.model.operator.OperatorUninstallPolicy;
import lombok.Data;

import java.util.List;

@Data
public class OperatorCatalogRequestDTO {
    private String name;
    private String packageName;
    private String channel;
    private String targetNamespace;
    private String desiredVersion;
    private String manifestYaml;
    private OperatorUninstallPolicy uninstallPolicy;
    private List<ManagedCrdRefDTO> managedCrds;
}
