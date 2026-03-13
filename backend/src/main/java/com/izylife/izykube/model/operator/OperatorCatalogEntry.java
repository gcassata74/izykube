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

package com.izylife.izykube.model.operator;

import com.izylife.izykube.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "operatorCatalog")
public class OperatorCatalogEntry extends BaseEntity {

    private String name;
    private String packageName;
    private String channel;
    private String targetNamespace;
    private String desiredVersion;
    private String installedVersion;
    private OperatorUninstallPolicy uninstallPolicy = OperatorUninstallPolicy.RETAIN_CRDS;
    private OperatorInstallStatus status = OperatorInstallStatus.NOT_INSTALLED;
    private String lastMessage;
    private LocalDateTime lastActionAt;
    private String manifestYaml;
    private List<ManagedCrdRef> managedCrds = new ArrayList<>();
}
