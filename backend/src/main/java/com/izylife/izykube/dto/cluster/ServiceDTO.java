package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceDTO extends NodeDTO {

    private String type;
    private int port;
    private Integer nodePort;

    @JsonCreator
    public ServiceDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("port") int port,
            @JsonProperty("nodePort") Integer nodePort
    ) {
        super(id, name, "service");
        this.type = type;
        this.port = port;
        this.nodePort = nodePort;
    }

    @Override
    public String create(KubernetesClient client) {
        ServicePort servicePort = new ServicePort();
        servicePort.setPort(port);
        if ("NodePort".equals(type) && nodePort != null) {
            servicePort.setNodePort(nodePort);
        }

        Map<String, String> selector = new HashMap<>();

        for (NodeDTO linkedNode : linkedNodes) {
            if (linkedNode instanceof DeploymentDTO) {
                DeploymentDTO deploymentDTO = (DeploymentDTO) linkedNode;
                selector.put("app", deploymentDTO.getName());
                break;
            }
        }

        Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(getName())
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withType(type)
                .withPorts(servicePort)
                .withSelector(selector)
                .endSpec()
                .build();

        return Serialization.asYaml(service);
    }

    public void setNodePort(Integer port) {
        if ("NodePort".equals(this.type)) {
            this.nodePort = port;
        }
    }

    public void changeType(String newType) {
        this.type = newType;
        if (!"NodePort".equals(newType)) {
            this.nodePort = null;
        }
    }
}