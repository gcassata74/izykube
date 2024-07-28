package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import com.izylife.izykube.dto.cluster.ConfigMapEntryDTO;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Processor(ConfigMapDTO.class)
@Service
public class ConfigMapProcessor implements TemplateProcessor<ConfigMapDTO> {

    public String createTemplate(ConfigMapDTO dto) {

        Map<String, String> data = mergeEntries(dto.getEntries());

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace("default")
                .endMetadata()
                .withData(data)
                .build();

        return Serialization.asYaml(configMap);
    }

    private Map<String, String> mergeEntries(List<ConfigMapEntryDTO> entries) {
        Map<String, String> mergedMap = new HashMap<>();
        for (ConfigMapEntryDTO entry : entries) {
            mergedMap.put(entry.getKey(), entry.getValue());
        }
        return mergedMap;
    }
}
