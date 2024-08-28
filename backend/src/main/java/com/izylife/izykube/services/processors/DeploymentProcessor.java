package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.DeploymentDTO;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

@Processor(DeploymentDTO.class)
@Service
public class DeploymentProcessor implements TemplateProcessor<DeploymentDTO> {

    @Override
    public String createTemplate(DeploymentDTO dto) {
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withReplicas(dto.getReplicas())
                .withNewStrategy()
                .withType(dto.getStrategy().getType())
                .withNewRollingUpdate()
                .withMaxSurge(dto.getStrategy().getRollingUpdate().getMaxSurge())
                .withMaxUnavailable(dto.getStrategy().getRollingUpdate().getMaxUnavailable())
                .endRollingUpdate()
                .endStrategy()
                .withMinReadySeconds(dto.getMinReadySeconds())
                .withRevisionHistoryLimit(dto.getRevisionHistoryLimit())
                .withProgressDeadlineSeconds(dto.getProgressDeadlineSeconds())
                .endSpec()
                .build();

        return Serialization.asYaml(deployment);
    }
}