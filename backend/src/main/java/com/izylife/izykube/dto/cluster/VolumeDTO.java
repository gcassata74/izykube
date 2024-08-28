package com.izylife.izykube.dto.cluster;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class VolumeDTO extends NodeDTO {
    private String type;
    private Map<String, Object> config;

    public VolumeDTO(String id, String name, String type, Map<String, Object> config) {
        super(id, name, "volume");
        this.type = type;
        this.config = config;
    }
}