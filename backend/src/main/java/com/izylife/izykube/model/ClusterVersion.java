package com.izylife.izykube.model;

import com.izylife.izykube.collections.ClusterStatusEnum;
import com.izylife.izykube.dto.cluster.LinkDTO;
import com.izylife.izykube.dto.cluster.NodeDTO;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "cluster_version")
public class ClusterVersion {
    @Id
    private String id;
    private String clusterId;
    private String clusterName;
    private String namespace;
    private int versionNumber;
    private String diagram;
    private List<NodeDTO> nodes;
    private List<LinkDTO> links;
    private ClusterStatusEnum status;
    private Instant createdAt;
}
