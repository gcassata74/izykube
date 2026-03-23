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
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.Arrays;
import java.util.Date;

@ChangeLog(order = "002")
public class ClusterChangeLog {

    @ChangeSet(order = "001", id = "insertTestClusterData", author = "Giuseppe Cassata")
    public void insertTestClusterData(MongoDatabase db) {
        MongoCollection<Document> collection = db.getCollection("cluster");

        long count = db.getCollection("cluster").countDocuments(new Document("name", "Test Cluster"));
        if (count == 0) {

            // Example NodeDTO documents
            Document node1 = new Document("id", "node1")
                    .append("name", "Node One")
                    .append("kind", "Pod");

            Document node2 = new Document("id", "node2")
                    .append("name", "Node Two")
                    .append("kind", "Deployment");

            // Example Link document
            Document link = new Document("id", "link1")
                    .append("sourceNodeId", "node1")
                    .append("targetNodeId", "node2");

            // Creating the Cluster document with all fields
            Document cluster = new Document()
                    .append("id", "cluster1")
                    .append("name", "Test Cluster")
                    .append("nameSpace", "default")
                    .append("nodes", Arrays.asList(node1, node2))
                    .append("links", Arrays.asList(link))
                    .append("diagram", "Sample Diagram Data")
                    .append("creationDate", new Date())
                    .append("lastUpdated", new Date());

            collection.insertOne(cluster);
        }
    }

    @ChangeSet(order = "002", id = "addStatusFieldsToCluster", author = "developer")
    public void addStatusFieldsToCluster(MongoDatabase db) {
        db.getCollection("cluster").updateMany(
                new Document(),
                new Document("$set", new Document("hasTemplate", false).append("isDeployed", false))
        );
    }
}
