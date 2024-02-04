package com.izylife.izykube.model;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "assets")
public class Asset extends BaseEntity {

    private String name;
    private String type;
    private String version;
    private String description;
    private String image;

}
