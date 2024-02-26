package com.izylife.izykube;

import io.mongock.runner.springboot.EnableMongock;
import lombok.Generated;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Generated
@EnableMongock
@EnableMongoAuditing
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class IzykubeApplication {

  public static void main(String[] args) {
    SpringApplication.run(IzykubeApplication.class, args);
  }
}
