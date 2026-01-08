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
    private String serviceAccountName;

    @JsonCreator
    public JobDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("assetId") String assetId,
            @JsonProperty("serviceAccountRef") String serviceAccountRef,
            @JsonProperty("serviceAccountName") String serviceAccountName
    ) {
        super(id, name, "job");
        this.assetId = assetId;
        this.serviceAccountRef = serviceAccountRef;
        this.serviceAccountName = serviceAccountName;
    }

    public JobDTO(String id, String name, String assetId) {
        this(id, name, assetId, null, null);
    }

    public JobDTO(String id, String name, String assetId, String serviceAccountRef) {
        this(id, name, assetId, serviceAccountRef, null);
    }
}
