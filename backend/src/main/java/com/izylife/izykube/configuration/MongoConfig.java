package com.izylife.izykube.configuration;

import com.izylife.izykube.model.AssetTypeReadConverter;
import com.izylife.izykube.model.AssetTypeWriteConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.Arrays;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new AssetTypeReadConverter(),
                new AssetTypeWriteConverter()
        ));
    }
}
