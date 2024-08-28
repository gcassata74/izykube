package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.PodDTO;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

@Processor(PodDTO.class)
@Service
public class PodProcessor implements TemplateProcessor<PodDTO> {

    @Override
    public String createTemplate(PodDTO dto) {
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy(dto.getRestartPolicy())
                .withServiceAccountName(dto.getServiceAccountName())
                .withNodeSelector(dto.getNodeSelector())
                .withHostNetwork(dto.getHostNetwork())
                .withDnsPolicy(dto.getDnsPolicy())
                .withSchedulerName(dto.getSchedulerName())
                .withPriority(dto.getPriority())
                .withPreemptionPolicy(dto.getPreemptionPolicy())
                .endSpec()
                .build();

        return Serialization.asYaml(pod);
    }
}