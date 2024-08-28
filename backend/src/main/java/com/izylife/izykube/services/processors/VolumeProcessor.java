package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.VolumeDTO;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

@Processor(VolumeDTO.class)
@Service
public class VolumeProcessor implements TemplateProcessor<VolumeDTO> {

    @Override
    public String createTemplate(VolumeDTO dto) {
        VolumeBuilder volumeBuilder = new VolumeBuilder()
                .withName(dto.getName());

        switch (dto.getType().toLowerCase()) {
            case "emptydir":
                volumeBuilder.withNewEmptyDir()
                        .withMedium((String) dto.getConfig().get("medium"))
                        .withSizeLimit(Quantity.parse((String) dto.getConfig().get("sizeLimit")))
                        .endEmptyDir();
                break;
            case "hostpath":
                volumeBuilder.withNewHostPath()
                        .withPath((String) dto.getConfig().get("path"))
                        .withType((String) dto.getConfig().get("type"))
                        .endHostPath();
                break;
            // Add more cases for other volume types as needed
            default:
                throw new IllegalArgumentException("Unsupported volume type: " + dto.getType());
        }

        Volume volume = volumeBuilder.build();
        return Serialization.asYaml(volume);
    }
}