package com.izylife.izykube.configuration;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/************************************************************************
 * Created: 26/08/22                                                    *
 * Author: Giuseppe Cassata                                             *
 ************************************************************************/
@Configuration
public class KubeConfiguration {

  @Bean
  public KubernetesClient kubernetesClient(){
    KubernetesClient client =new KubernetesClientBuilder().build();
    return client;
  }
}
