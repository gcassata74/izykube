/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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