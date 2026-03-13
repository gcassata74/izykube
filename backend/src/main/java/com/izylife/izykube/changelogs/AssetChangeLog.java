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

package com.izylife.izykube.changelogs;

import com.github.cloudyrock.mongock.ChangeLog;
import com.github.cloudyrock.mongock.ChangeSet;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

@ChangeLog(order = "001")
public class AssetChangeLog {

    @ChangeSet(order = "001", id = "createAssetsCollection", author = "gcassata")
    public void createAssetsCollection(MongoDatabase db) {
        db.createCollection("assets");
    }

    @ChangeSet(order = "002", id = "createIndexesForAssetsCollection", author = "gcassata")
    public void createIndexesForAssetsCollection(MongoDatabase db) {
        db.getCollection("assets").createIndex(Indexes.ascending("name"));
        db.getCollection("assets").createIndex(Indexes.compoundIndex(Indexes.ascending("type"), Indexes.ascending("image")));
    }

    @ChangeSet(order = "003", id = "insertExampleAssets", author = "gcassata", runAlways = true)
    public void insertExampleAssets(MongoDatabase db) {

        long count = db.getCollection("assets").countDocuments(new Document("name", "keycloak"));
        if (count == 0) {
            Document keycloakAsset = new Document()
                    .append("name", "keycloak")
                    .append("version", "1.0.0")
                    .append("description", "Open Source Identity and Access Management")
                    .append("image", "keycloak/keycloak:latest")
                    .append("type", "image")
                    .append("source", "BUILT_IN")
                    .append("port", "8080")
                    .append("creationDate", new java.util.Date())
                    .append("lastUpdated", new java.util.Date());

            db.getCollection("assets").insertOne(keycloakAsset);
        }
    }

    @ChangeSet(order = "004", id = "ensureAssetSourceField", author = "gcassata", runAlways = true)
    public void ensureAssetSourceField(MongoDatabase db) {
        Document missingSourceFilter = new Document("source", new Document("$exists", false));
        Document update = new Document("$set", new Document("source", "USER"));
        db.getCollection("assets").updateMany(missingSourceFilter, update);
    }
}
