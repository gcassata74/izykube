package com.izylife.izykube.dto.cluster;

import lombok.Data;

@Data
public class ConfigEntryDTO {
    private String key;
    private String value;
    private ConfigEntrySensitivity sensitivity = ConfigEntrySensitivity.PLAIN;
}

