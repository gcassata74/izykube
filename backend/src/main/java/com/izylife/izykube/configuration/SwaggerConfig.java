package com.izylife.izykube.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {


  /**
   * Information that is associated with the swagger models that are specific to this application server.
   */
  @Bean
  public OpenAPI openAPI(@Value("${server.servlet.context-path}") String contextPath) {
    return new OpenAPI()
            .addServersItem(new Server().url(contextPath))
            .openapi("3.0.0")
            .info(new Info().title("IZYKUBE SERVER REST API")
                    .description("IZYKUBE project. Server API"));
  }
}



