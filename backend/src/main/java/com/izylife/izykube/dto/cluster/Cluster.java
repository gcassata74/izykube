package com.izylife.izykube.dto.cluster;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data

public class Cluster {
    private String id;
    private String name;
    private List<? extends Base> nodes;
    private List<Link> links;
    private String diagram;
    private Date creationDate;
    private Date lastUpdated;
}
