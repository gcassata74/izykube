package com.izylife.izykube.configuration;

import io.fabric8.istio.client.DefaultIstioClient;
import io.fabric8.istio.client.IstioClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IstioClientConfig {

    @Bean
    public IstioClient istioClient(KubernetesClient kubernetesClient) {
        // Use the same configuration as the KubernetesClient
        Config config = new ConfigBuilder(kubernetesClient.getConfiguration()).build();
        return new DefaultIstioClient(config);
    }
}
