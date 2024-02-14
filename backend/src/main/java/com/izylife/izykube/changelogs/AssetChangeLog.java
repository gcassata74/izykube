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
        db.getCollection("assets").createIndex(Indexes.ascending("type"));
        // Aggiungi altri indici in base alle tue esigenze di query
    }

    @ChangeSet(order = "003", id = "insertExampleAssets", author = "gcassata", runAlways = true)
    public void insertExampleAssets(MongoDatabase db) {

        long count = db.getCollection("assets").countDocuments(new Document("name", "Keycloak"));
        if (count == 0) {
            Document keycloakAsset = new Document()
                    .append("label", "keycloak")
                    .append("type", "container")
                    .append("version", "1.0.0")
                    .append("description", "Open Source Identity and Access Management")
                    .append("image", "keycloak/keycloak:latest")
                    .append("creationDate", new java.util.Date())
                    .append("lastUpdated", new java.util.Date());

            db.getCollection("assets").insertOne(keycloakAsset);
        }
    }
}
