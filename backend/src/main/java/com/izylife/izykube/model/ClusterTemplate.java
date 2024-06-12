package com.izylife.izykube.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "clusterTemplates")
public class ClusterTemplate {

    @Id
    private String id;
    private String clusterId;
    private List<String> yamlList;

}
