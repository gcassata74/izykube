package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ConfigMapDTO;
import com.izylife.izykube.dto.cluster.ContainerDTO;
import com.izylife.izykube.dto.cluster.JobDTO;
import com.izylife.izykube.dto.cluster.ServiceDTO;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Processor(JobDTO.class)
@Service
@AllArgsConstructor
public class JobProcessor implements TemplateProcessor<JobDTO> {


    private final AssetRepository assetRepository;
    private final ContainerProcessor containerProcessor;
    private final ConfigMapProcessor configMapProcessor;

    @Override
    public String createTemplate(JobDTO dto) {
        Asset asset = assetRepository.findById(dto.getAssetId())
                .orElseThrow(() -> new NoSuchElementException("Asset not found: " + dto.getAssetId()));

        ServiceDTO sourceService = dto.getSourceNodes().stream()
                .filter(node -> node instanceof ServiceDTO)
                .map(node -> (ServiceDTO) node)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Job must be connected to a target service"));

        StringBuilder yaml = new StringBuilder();

        // Create script ConfigMap using ConfigMapProcessor
        ConfigMapDTO scriptConfigMap = createScriptConfigMapDTO(dto, asset);
        yaml.append(configMapProcessor.createTemplate(scriptConfigMap));
        yaml.append("---\n");

        // Create Job with init container
        yaml.append(createJobTemplate(dto, scriptConfigMap.getName(), sourceService, asset));

        return yaml.toString();
    }

    private ConfigMapDTO createScriptConfigMapDTO(JobDTO dto, Asset asset) {
        String configMapName = dto.getName() + "-script";

        // Create yaml content directly
        StringBuilder yamlBuilder = new StringBuilder();
        // Removed the extra 'data:' nesting
        yamlBuilder.append("script.sh: |\n");
        // Indent each line of the script with 2 spaces to maintain YAML format
        String[] lines = asset.getScript().split("\n");
        for (String line : lines) {
            yamlBuilder.append("  ").append(line).append("\n");
        }

        return new ConfigMapDTO(
                dto.getName() + "-script",
                configMapName,
                yamlBuilder.toString()
        );
    }

    private String createJobTemplate(JobDTO dto, String configMapName, ServiceDTO targetService, Asset asset) {
        List<EnvVar> envVars = createEnvironmentVariables(targetService);

        // Create main container using ContainerProcessor
        ContainerDTO mainContainerDto = new ContainerDTO(
                dto.getName(),
                dto.getName(),
                asset.getId(),
                targetService.getPort()
        );

        Container mainContainer = containerProcessor.processContainer(mainContainerDto, List.of(
                new VolumeMountBuilder()
                        .withName("script-volume")
                        .withMountPath("/scripts")
                        .build()
        ));
        mainContainer.setCommand(List.of("/bin/sh", "/scripts/script.sh"));
        mainContainer.setEnv(envVars);

        Job job = new JobBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withTtlSecondsAfterFinished(100)
                .withBackoffLimit(4)
                .withNewTemplate()
                .withNewSpec()
                .withContainers(mainContainer)
                .addNewVolume()
                .withName("script-volume")
                .withNewConfigMap()
                .withName(configMapName)
                .withDefaultMode(0755)
                .endConfigMap()
                .endVolume()
                .withRestartPolicy("Never")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        return Serialization.asYaml(job);
    }

    private List<EnvVar> createEnvironmentVariables(ServiceDTO targetService) {
        List<EnvVar> envVars = new ArrayList<>();
        String serviceName = targetService.getName().toUpperCase().replace("-", "_");

        envVars.add(new EnvVar(
                serviceName + "_SERVICE_HOST",
                targetService.getName() + ".default.svc.cluster.local",
                null
        ));
        envVars.add(new EnvVar(
                serviceName + "_SERVICE_PORT",
                String.valueOf(targetService.getPort()),
                null
        ));
        envVars.add(new EnvVar(
                "TARGET_SERVICE",
                targetService.getName(),
                null
        ));

        return envVars;
    }

}


