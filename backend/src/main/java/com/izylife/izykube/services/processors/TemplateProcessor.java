package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.NodeDTO;

public interface TemplateProcessor<T extends NodeDTO> {
    public String createTemplate(T dto);
}
