package com.izylife.izykube.dto.cluster;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Ingress extends Base {

    private String id;
    private String name;
    private Map<String, String> annotations; // Optional, can be null
    private List<IngressRule> rules;

    @Data
    public static class IngressRule {
        private String host;
        private Http http;

        @Data
        public static class Http {
            private List<IngressPath> paths;
        }
    }

    @Data
    public static class IngressPath {
        private String path;
        private Backend backend;

        @Data
        public static class Backend {
            private String serviceName;
            private int servicePort;
        }
    }

}
