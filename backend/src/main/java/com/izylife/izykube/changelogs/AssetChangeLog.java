package com.izylife.izykube.changelogs;

import com.github.cloudyrock.mongock.ChangeLog;
import com.github.cloudyrock.mongock.ChangeSet;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

@ChangeLog(order = "001")
public class AssetChangeLog {

    @ChangeSet(order = "001", id = "createAssetsCollection", author = "yourName")
    public void createAssetsCollection(MongoDatabase db) {
        db.createCollection("assets");
    }

    @ChangeSet(order = "002", id = "createIndexesForAssetsCollection", author = "yourName")
    public void createIndexesForAssetsCollection(MongoDatabase db) {
        db.getCollection("assets").createIndex(Indexes.ascending("name"));
        db.getCollection("assets").createIndex(Indexes.ascending("type"));
        // Aggiungi altri indici in base alle tue esigenze di query
    }

    @ChangeSet(order = "003", id = "insertExampleAssets", author = "yourName", runAlways = true)
    public void insertExampleAssets(MongoDatabase db) {
        Document keycloakAsset = new Document()
                .append("name", "Keycloak")
                .append("type", "Security")
                .append("version", "1.0.0")
                .append("description", "Open Source Identity and Access Management")
                .append("metadata", new Document("category", "Authentication").append("tags", java.util.Arrays.asList("SSO", "security", "identity management")))
                .append("image", "keycloak/keycloak:latest")
                .append("creationDate", new java.util.Date())
                .append("lastUpdated", new java.util.Date());

        db.getCollection("assets").insertOne(keycloakAsset);

        // Aggiungi altri documenti di esempio se necessario
    }
}
