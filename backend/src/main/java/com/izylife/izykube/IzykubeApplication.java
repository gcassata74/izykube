package com.izylife.izykube;

import lombok.Generated;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

@Generated
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class IzykubeApplication {

  public static void main(String[] args) {
    SpringApplication.run(IzykubeApplication.class, args);
  }
}
