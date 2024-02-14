package com.izylife.izykube.dto.cluster;


import lombok.Data;

import java.util.List;

@Data
public class ConfigMap {
    private String name;
    private List<ConfigMapItem> items;

    @Data
    public class ConfigMapItem {
        private String key;
        private String path;
    }
}


