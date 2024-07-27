package com.izylife.izykube.factory;

import com.izylife.izykube.dto.cluster.*;
import com.izylife.izykube.services.processors.*;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TemplateFactory {
    private final Map<Class<? extends NodeDTO>, Class<? extends TemplateProcessor<?>>> processorMap = new HashMap<>();
    private final ApplicationContext context;

    public TemplateFactory(ApplicationContext context) {
        this.context = context;
        initProcessorMap();
    }

    private void initProcessorMap() {
        processorMap.put(ConfigMapDTO.class, ConfigMapProcessor.class);
        processorMap.put(ContainerDTO.class, ContainerProcessor.class);
        processorMap.put(DeploymentDTO.class, DeploymentProcessor.class);
        processorMap.put(ServiceDTO.class, ServiceProcessor.class);
        processorMap.put(IngressDTO.class, IngressProcessor.class);
    }

    @SuppressWarnings("unchecked")
    public <T extends NodeDTO> TemplateProcessor<T> getProcessor(T dto) {
        Class<? extends TemplateProcessor<?>> processorClass = processorMap.get(dto.getClass());
        if (processorClass == null) {
            throw new IllegalArgumentException("No processor found for DTO class: " + dto.getClass().getSimpleName());
        }
        return (TemplateProcessor<T>) context.getBean(processorClass);
    }
}