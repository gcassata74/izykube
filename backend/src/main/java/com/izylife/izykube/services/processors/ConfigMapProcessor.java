package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

@Processor(ConfigMapDTO.class)
@Service
public class ConfigMapProcessor implements TemplateProcessor<ConfigMapDTO> {

    @Override
    public String createTemplate(ConfigMapDTO dto) {
        Yaml yamlParser = new Yaml();
        Map<String, String> data = yamlParser.load(dto.getYaml());

        // Handle cases where parsing fails or returns an unexpected structure
        if (data == null) {
            throw new IllegalArgumentException("Invalid YAML content provided");
        }

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace("default")
                .endMetadata()
                .withData(data)
                .build();

        return Serialization.asYaml(configMap);
    }
}