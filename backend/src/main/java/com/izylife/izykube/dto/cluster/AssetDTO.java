package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.izylife.izykube.enums.AssetType;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssetDTO {
    private String id;
    private String name;
    private AssetType type;
    private String yaml;
    private String description;
    private int port;
    private String image;
    private String version;
}
