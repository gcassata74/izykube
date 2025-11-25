package com.izylife.izykube.dto.cluster;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class VirtualServiceDTO extends NodeDTO {
    private String host;
    private String path;
    private String serviceName;
    private int servicePort;

    public VirtualServiceDTO(String id, String name, String host, String path, String serviceName, int servicePort) {
        super(id, name, "istio");
        this.host = host;
        this.path = path;
        this.serviceName = serviceName;
        this.servicePort = servicePort;
    }
}
