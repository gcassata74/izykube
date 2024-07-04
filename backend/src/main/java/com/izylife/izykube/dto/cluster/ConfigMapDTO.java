package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ConfigMapDTO extends NodeDTO {

    private List<Map<String, String>> entries;

    @JsonCreator
    public ConfigMapDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("entries") List<Map<String, String>> entries
    ) {
        super(id, name, "configmap");
        this.entries = entries;
    }

    private Map<String, String> mergeEntries(List<Map<String, String>> entries) {
        Map<String, String> mergedMap = new HashMap<>();
        for (Map<String, String> entry : entries) {
            mergedMap.put(entry.get("key"), entry.get("value"));
        }
        return mergedMap;
    }


    @Override
    public String create(KubernetesClient client) {

        Map<String, String> data = mergeEntries(this.entries);

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace("default")
                .endMetadata()
                .withData(data)
                .build();

        return Serialization.asYaml(configMap);
    }


}
