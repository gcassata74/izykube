package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.ContainerDTO;
import org.springframework.stereotype.Service;

@Service
@Processor(ContainerDTO.class)
public class ContainerProcessor implements TemplateProcessor<ContainerDTO> {

    public String createTemplate(ContainerDTO dto) {
        return "";
    }
}
