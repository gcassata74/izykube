package com.izylife.izykube.factory;

import com.izylife.izykube.dto.cluster.NodeDTO;
import com.izylife.izykube.services.processors.Processor;
import com.izylife.izykube.services.processors.TemplateProcessor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TemplateFactory {
    private final Map<Class<? extends NodeDTO>, TemplateProcessor<? extends NodeDTO>> processors;

    public TemplateFactory(List<TemplateProcessor<? extends NodeDTO>> processorList) {
        processors = new HashMap<>();
        for (TemplateProcessor<? extends NodeDTO> processor : processorList) {
            Processor annotation = processor.getClass().getAnnotation(Processor.class);
            if (annotation != null) {
                processors.put(annotation.value(), processor);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends NodeDTO> TemplateProcessor<T> getProcessor(T dto) {
        TemplateProcessor<? extends NodeDTO> processor = processors.get(dto.getClass());
        if (processor == null) {
            throw new IllegalArgumentException("No processor found for DTO class: " + dto.getClass().getSimpleName());
        }
        return (TemplateProcessor<T>) processor;
    }
}