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

        StringBuilder fullYaml = new StringBuilder();
        Map<String, String> labels = Map.of("app", name);
        List<EnvFromSource> envFromSources = new ArrayList<>();

        for (NodeDTO linkedNode : linkedNodes) {
            if (linkedNode instanceof ConfigMapDTO) {
                ConfigMapDTO configMapDTO = (ConfigMapDTO) linkedNode;
                String configMapYaml = configMapDTO.create(client);
                fullYaml.append(configMapYaml);

                // Add ConfigMap as an environment source
                envFromSources.add(new EnvFromSourceBuilder()
                        .withNewConfigMapRef()
                        .withName(configMapDTO.getName())
                        .endConfigMapRef()
                        .build());
            } else if (linkedNode instanceof ServiceDTO) {
                ServiceDTO serviceDTO = (ServiceDTO) linkedNode;
                Service service = new ServiceBuilder()
                        .withNewMetadata()
                        .withName(serviceDTO.getName())
                        .withNamespace("default")
                        .endMetadata()
                        .withNewSpec()
                        .withType(serviceDTO.getType())
                        .addNewPort()
                        .withPort(serviceDTO.getPort())
                        .withNodePort(serviceDTO.getNodePort())
                        .endPort()
                        .withSelector(labels)
                        .endSpec()
                        .build();
                fullYaml.append(Serialization.asYaml(service));
            }
        }

        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withReplicas(replicas)
                .withNewSelector()
                .withMatchLabels(labels)
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(name)
                .withImage(assetId)
                .addNewPort()
                .withContainerPort(containerPort)
                .endPort()
                .withResources(createResourceRequirements())
                .withEnvFrom(envFromSources)  // Add environment variables from ConfigMaps
                .endContainer()
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