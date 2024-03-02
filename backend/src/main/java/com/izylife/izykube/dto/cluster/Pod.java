package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Pod extends NodeDTO {

    private String assetId;

    @JsonCreator
    public Pod(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("assetId") String assetId
    ) {
        super(id, name, "pod");
        this.assetId = assetId;
    }


}
