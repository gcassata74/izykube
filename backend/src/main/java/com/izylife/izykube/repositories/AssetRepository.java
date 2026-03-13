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

package com.izylife.izykube.repositories;
import com.izylife.izykube.enums.AssetType;
import com.izylife.izykube.model.Asset;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends MongoRepository<Asset, String> {
    List<Asset> findByType(AssetType type);

    boolean existsByTypeAndImageIgnoreCase(AssetType type, String image);

    Optional<Asset> findByTypeAndImageIgnoreCase(AssetType type, String image);

    Optional<Asset> findFirstByTypeAndNameIgnoreCaseAndVersionIgnoreCase(AssetType type, String name, String version);
}
