package com.izylife.izykube.configuration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/************************************************************************
 * Created: 12/08/22                                                    *
 * Author: Giuseppe Cassata                                             *
 ************************************************************************/
;

@Configuration
public class DockerConfiguration {

  @Bean
  public DockerClient dockerClient() {
    var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost("unix:///var/run/docker.sock")
            // If you're using Docker credentials, set them here.
            //.withDockerTlsVerify(true)
            //.withDockerCertPath("/path/to/cert")
            //.withDockerConfig("/path/to/config")
            //.withRegistryUrl("https://index.docker.io/v1/")
            //.withRegistryUsername("dockeruser")
            //.withRegistryPassword("dockerpass")
            //.withRegistryEmail("dockeremail")
            .build();

    return DockerClientImpl.getInstance(config);
  }

}
