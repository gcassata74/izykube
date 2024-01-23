package com.izylife.izykube.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeploymentRequest {
    private String deploymentName;
    private String imageName;
    private String namespace;
    private Integer replicas;
}
