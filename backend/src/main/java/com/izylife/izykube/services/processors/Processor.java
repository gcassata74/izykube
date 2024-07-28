package com.izylife.izykube.services.processors;

import com.izylife.izykube.dto.cluster.NodeDTO;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Processor {
    Class<? extends NodeDTO> value();
}
