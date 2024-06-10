package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Data;

@Data
public class Container extends NodeDTO {
    private String assetId;

    @JsonCreator
    public Container(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("assetId") String assetId
    ) {
        super(id, name, "Container");
        this.assetId = assetId;
    }

    @Override
    public String create(KubernetesClient client) {
        return "";
    }

}