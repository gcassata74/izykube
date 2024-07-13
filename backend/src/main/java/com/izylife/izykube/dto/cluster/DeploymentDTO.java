package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeploymentDTO extends NodeDTO {

    private String assetId;
    private int replicas;
    private int containerPort;
    private Map<String, String> resources;
    private List<EnvVarDTO> envVars;

    @JsonCreator
    public DeploymentDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("assetId") String assetId,
            @JsonProperty("replicas") int replicas,
            @JsonProperty("containerPort") int containerPort,
            @JsonProperty("resources") Map<String, String> resources
    ) {
        super(id, name, "deployment");
        this.assetId = assetId;
        this.replicas = replicas;
        this.containerPort = containerPort;
        this.resources = resources;
    }

    @Override
    public String create(KubernetesClient client) {
        List<Volume> volumes = new ArrayList<>();
        List<VolumeMount> volumeMounts = new ArrayList<>();
        StringBuilder fullYaml = new StringBuilder();

        for (NodeDTO linkedNode : linkedNodes) {
            if (linkedNode instanceof ConfigMapDTO) {
                ConfigMapDTO configMapDTO = (ConfigMapDTO) linkedNode;
                String yaml = configMapDTO.create(client);
                InputStream yamlStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
                List<HasMetadata> items = client.load(yamlStream).get();
                if (!items.isEmpty() && items.get(0) instanceof ConfigMap) {
                    ConfigMap configMap = (ConfigMap) items.get(0);
                    volumes.add(new VolumeBuilder()
                            .withName(configMap.getMetadata().getName())
                            .withNewConfigMap()
                            .withName(configMap.getMetadata().getName())
                            .endConfigMap()
                            .build());
                    volumeMounts.add(new VolumeMountBuilder()
                            .withName(configMap.getMetadata().getName())
                            .withMountPath("/etc/config")
                            .build());
                }
                fullYaml.append(yaml).append("\n---\n");
            } else if (linkedNode instanceof ServiceDTO) {
                ServiceDTO serviceDTO = (ServiceDTO) linkedNode;
                String serviceYaml = serviceDTO.create(client);
                fullYaml.append(serviceYaml).append("\n---\n");
            }
            // Add handling for other types of linked nodes (e.g., volumes) here
        }

        io.fabric8.kubernetes.api.model.Container container = new ContainerBuilder()
                .withName(name)
                .withImage(assetId)
                .addNewPort()
                .withContainerPort(containerPort)
                .endPort()
                .withResources(createResourceRequirements())
                .withEnv(createEnvVarList())
                .withVolumeMounts(volumeMounts)
                .build();

        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withReplicas(replicas)
                .withNewSelector()
                .addToMatchLabels("app", name)
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("app", name)
                .endMetadata()
                .withNewSpec()
                .addToContainers(container)
                .withVolumes(volumes)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        fullYaml.append(Serialization.asYaml(deployment));
        return fullYaml.toString();
    }

    private ResourceRequirements createResourceRequirements() {
        return new ResourceRequirementsBuilder()
                .addToRequests("cpu", new Quantity(resources.get("cpu")))
                .addToRequests("memory", new Quantity(resources.get("memory")))
                .build();
    }

    private List<EnvVar> createEnvVarList() {
        return envVars.stream()
                .map(envVarDTO -> new EnvVarBuilder()
                        .withName(envVarDTO.getName())
                        .withValue(envVarDTO.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Data
    public static class EnvVarDTO {
        private String name;
        private String value;

        @JsonCreator
        public EnvVarDTO(
                @JsonProperty("name") String name,
                @JsonProperty("value") String value
        ) {
            this.name = name;
            this.value = value;
        }
    }
}