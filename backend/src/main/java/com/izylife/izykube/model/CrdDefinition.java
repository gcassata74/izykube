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

package com.izylife.izykube.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "crds")
public class CrdDefinition extends BaseEntity implements Persistable<String> {

    /**
     * spec.group
     */
    private String group;

    /**
     * spec.names.singular (input field)
     */
    private String singularName;

    /**
     * spec.scope (Namespaced|Cluster)
     */
    private String scope;

    /**
     * spec.versions[0].name
     */
    private String version = "v1";

    /**
     * Schema fields under spec (openAPIV3Schema.properties.spec.properties)
     */
    private List<CrdSchemaField> schemaFields = new ArrayList<>();

    @Transient
    private boolean persisted = false;

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @Override
    public void setId(String id) {
        super.setId(id);
        this.persisted = true;
    }
}
