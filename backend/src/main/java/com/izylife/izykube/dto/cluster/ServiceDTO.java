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
    private IntOrString targetPort;
    private String protocol;
    private Integer nodePort;

    @JsonCreator
    public ServiceDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("port") int port,
            @JsonProperty("targetPort") IntOrString targetPort,
            @JsonProperty("protocol") String protocol,
            @JsonProperty("nodePort") Integer nodePort
    ) {
        super(id, name, "service");
        this.type = type;
        this.port = port;
        this.targetPort = targetPort;
        this.protocol = protocol;
        this.nodePort = nodePort;
    }

    @Override
    public String create(KubernetesClient client) {
        Map<String, String> selector = new HashMap<>();

        for (NodeDTO linkedNode : linkedNodes) {
            if (linkedNode instanceof DeploymentDTO || linkedNode instanceof PodDTO) {
                selector.put("app", linkedNode.getName());
                break;  // Assume we're only linking to one deployment or pod
            }
        }

        ServicePort servicePort = new ServicePort();
        servicePort.setPort(port);
        servicePort.setTargetPort(targetPort);
        servicePort.setProtocol(protocol);
        if ("NodePort".equals(type) && nodePort != null) {
            servicePort.setNodePort(nodePort);
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
}