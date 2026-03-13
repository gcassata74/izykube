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

import com.izylife.izykube.enums.AssetSource;
import com.izylife.izykube.enums.AssetType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "assets")
public class Asset extends BaseEntity implements Persistable<String> {

    private String name;
    @Field("type")
    private AssetType type;
    private String script;
    private String version;
    private String description;
    /**
     * Full image reference (repository/name:tag) when the asset type is IMAGE.
     */
    private String image;
    private int port;
    private AssetSource source = AssetSource.USER;

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

    public AssetSource getSource() {
        return source != null ? source : AssetSource.USER;
    }

}
