package com.izylife.izykube.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "portForwards")
public class PortForwardEntry extends BaseEntity {
    private String namespace;
    private String serviceName;
    private int localPort;
    private int targetPort;
    private boolean active;
}
