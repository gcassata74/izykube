package com.izylife.izykube.configuration;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/************************************************************************
 * Created: 12/08/22                                                    *
 * Author: Giuseppe Cassata                                             *
 ************************************************************************/
;import java.time.Duration;

@Configuration
public class DockerConfiguration {

  @Bean
  public DockerClientConfig dockerConfig() {
    return DefaultDockerClientConfig.createDefaultConfigBuilder()
      //.withDockerHost("tcp://docker.somewhere.tld:2376")
      .withDockerHost("unix:///var/run/docker.sock")
      .withDockerTlsVerify(false)
      //.withDockerCertPath("/home/user/.docker")
      .withRegistryUsername("gcassata")
      .withRegistryPassword("GCavlc741#")
      .withRegistryEmail("giuseppe.cassata74@gmail.com")
      .withRegistryUrl("https://registry.hub.docker.com")
      .build();
  }

  @Bean
  public ApacheDockerHttpClient dockerClient(){
    ApacheDockerHttpClient httpClient =  new ApacheDockerHttpClient.Builder()
      .dockerHost(dockerConfig().getDockerHost())
      .sslConfig(dockerConfig().getSSLConfig())
      .maxConnections(100)
      .connectionTimeout(Duration.ofSeconds(30))
      .responseTimeout(Duration.ofSeconds(45))
      .build();

    return httpClient;
  }

}
