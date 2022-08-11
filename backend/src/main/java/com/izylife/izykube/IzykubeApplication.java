package com.izylife.izykube;

import lombok.Generated;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Generated
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class IzykubeApplication {

  public static void main(String[] args) {
    SpringApplication.run(IzykubeApplication.class, args);
  }

}
