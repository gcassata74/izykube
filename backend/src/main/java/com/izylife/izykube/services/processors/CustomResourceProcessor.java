package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.CustomResourceDTO;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

@Processor(CustomResourceDTO.class)
@Service
public class CustomResourceProcessor implements TemplateProcessor<CustomResourceDTO> {

    @Override
    public String createTemplate(CustomResourceDTO dto) {
        String group = trim(dto.getCrdGroup());
        String version = trim(dto.getCrdVersion());
        String kind = trim(dto.getCrdKind());
        String name = trim(dto.getName());
        if (group.isEmpty() || version.isEmpty() || kind.isEmpty()) {
            throw new IllegalArgumentException("Custom Resource requires CRD group, version and kind.");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Custom Resource name is required.");
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", group + "/" + version);
        manifest.put("kind", kind);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", name);
        boolean namespaced = !"cluster".equalsIgnoreCase(trim(dto.getCrdScope()));
        if (namespaced) {
            String namespace = dto.getNamespace() == null || dto.getNamespace().isBlank() ? "default" : dto.getNamespace().trim();
            metadata.put("namespace", namespace);
        }
        manifest.put("metadata", metadata);
        manifest.put("spec", dto.getSpec() == null ? new LinkedHashMap<>() : dto.getSpec());

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(manifest);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
