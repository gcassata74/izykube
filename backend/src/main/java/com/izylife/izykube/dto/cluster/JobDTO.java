package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobDTO extends NodeDTO {


    private String assetId;
    private String serviceAccountRef;

    @JsonCreator
    public JobDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("assetId") String assetId,
            @JsonProperty("serviceAccountRef") String serviceAccountRef
    ) {
        super(id, name, "job");
        this.assetId = assetId;
        this.serviceAccountRef = serviceAccountRef;
    }
}
