package com.izylife.izykube.dto.kube;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentLogsDTO {
    private String name;
    private String namespace;
    private List<PodLogDTO> pods;
}
