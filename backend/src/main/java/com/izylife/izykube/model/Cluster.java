package com.izylife.izykube.model;

import com.izylife.izykube.collections.ClusterStatusEnum;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Data
@Document(collection = "cluster")
public class Cluster {
    @Id
    private String id;
    private String name;
    private String nameSpace = "default";
    private List<NodeDTO> nodes;
    private List<LinkDTO> links;
    private String diagram;
    private Date creationDate;
    private Date lastUpdated;
    private ClusterStatusEnum status;
}
