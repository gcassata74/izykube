package com.izylife.izykube.configuration;

import com.izylife.izykube.model.AssetTypeReadConverter;
import com.izylife.izykube.model.AssetTypeWriteConverter;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.Arrays;

@Configuration
public class MongoConfig {


    @Bean
    public BeanPostProcessor mappingMongoConverterCustomizer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof MappingMongoConverter converter) {
                    converter.setMapKeyDotReplacement("．");
                }
                return bean;
            }
        };
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new AssetTypeReadConverter(),
                new AssetTypeWriteConverter()
        ));
    }
}
