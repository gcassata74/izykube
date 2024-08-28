package com.izylife.izykube.dto.cluster;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class IngressDTO extends NodeDTO {
    private String host;
    private String path;
    private String serviceName;
    private int servicePort;

    public IngressDTO(String id, String name, String host, String path, String serviceName, int servicePort) {
        super(id, name, "ingress");
        this.host = host;
        this.path = path;
        this.serviceName = serviceName;
        this.servicePort = servicePort;
    }
}