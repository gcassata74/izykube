package com.izylife.izykube.dto.kube;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PodLogDetailsDTO {
    private String name;
    private String namespace;
    private String container;
    private String logs;
}

