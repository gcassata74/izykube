package com.izylife.izykube.dto.cluster;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentStrategyDTO {
    private String type;
    private RollingUpdateDTO rollingUpdate;
}
