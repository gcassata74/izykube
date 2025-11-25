package com.izylife.izykube.dto.cluster;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class IngressDTO extends NodeDTO {
    private String host;
    private String path;
    private String serviceName;
    private int servicePort;
    private String tls;
    private Map<String, String> annotations = new LinkedHashMap<>();

    public IngressDTO(String id, String name, String host, String path, String serviceName, int servicePort) {
        this(id, name, host, path, serviceName, servicePort, null, new LinkedHashMap<>());
    }

    public IngressDTO(String id,
                      String name,
                      String host,
                      String path,
                      String serviceName,
                      int servicePort,
                      String tls,
                      Map<String, String> annotations) {
        super(id, name, "ingress");
        this.host = host;
        this.path = path;
        this.serviceName = serviceName;
        this.servicePort = servicePort;
        this.tls = tls;
        this.annotations = annotations != null ? new LinkedHashMap<>(annotations) : new LinkedHashMap<>();
    }
}
