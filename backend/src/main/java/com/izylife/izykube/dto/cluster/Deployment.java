package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Deployment extends Base {

    private int replicas;
    private Selector selector;
    private PodTemplate template;

    @JsonCreator
    public Deployment(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("replicas") int replicas,
            @JsonProperty("selector") Selector selector,
            @JsonProperty("template") PodTemplate template) {
        super(id, name, "Deployment");
        this.replicas = replicas;
        this.selector = selector;
        this.template = template;
    }

    @Data
    public static class Selector {
        private Map<String, String> matchLabels;
        private List<SelectorExpression> matchExpressions;
    }

    @Data
    public static class SelectorExpression {
        private String key;
        private String operator;
        private List<String> values;
    }

    @Data
    public static class PodTemplate {
        private Map<String, String> metadata;
        private PodSpec spec;
    }

}
