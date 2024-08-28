package com.izylife.izykube.dto.cluster;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class PodDTO extends NodeDTO {
    private String restartPolicy;
    private String serviceAccountName;
    private Map<String, String> nodeSelector;
    private Boolean hostNetwork;
    private String dnsPolicy;
    private String schedulerName;
    private Integer priority;
    private String preemptionPolicy;

    public PodDTO(String id, String name, String restartPolicy) {
        super(id, name, "pod");
        this.restartPolicy = restartPolicy;
    }

    public PodDTO(String id, String name, String restartPolicy, String serviceAccountName,
                  Map<String, String> nodeSelector, Boolean hostNetwork, String dnsPolicy,
                  String schedulerName, Integer priority, String preemptionPolicy) {
        super(id, name, "pod");
        this.restartPolicy = restartPolicy;
        this.serviceAccountName = serviceAccountName;
        this.nodeSelector = nodeSelector;
        this.hostNetwork = hostNetwork;
        this.dnsPolicy = dnsPolicy;
        this.schedulerName = schedulerName;
        this.priority = priority;
        this.preemptionPolicy = preemptionPolicy;
    }
}