package com.izylife.izykube.dto.cluster;

import io.fabric8.kubernetes.api.model.IntOrString;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RollingUpdateDTO {
    private IntOrString maxUnavailable;
    private IntOrString maxSurge;
}