package com.izylife.izykube.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.LinkedList;

@Data
@Document(collection = "clusterTemplates")
public class ClusterTemplate {

    @Id
    private String id;
    private String clusterId;
    private LinkedList<String> yamlList;

}
