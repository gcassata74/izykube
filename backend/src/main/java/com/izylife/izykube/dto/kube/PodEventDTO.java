package com.izylife.izykube.dto.kube;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PodEventDTO {
    private String type;
    private String reason;
    private String message;
    private String timestamp;
    private Integer count;
}

