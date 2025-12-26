package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ServiceAccountDTO extends NodeDTO {

    private Boolean automountServiceAccountToken = true;
    private Map<String, String> labels = new LinkedHashMap<>();
    private Map<String, String> annotations = new LinkedHashMap<>();
    private String rbacProfile = "NONE";

    public ServiceAccountDTO(String id, String name) {
        super(id, name, "serviceaccount");
    }

    @Override
    @JsonProperty("namespace")
    public String getNamespace() {
        return super.getNamespace();
    }

    @Override
    @JsonProperty("namespace")
    public void setNamespace(String namespace) {
        super.setNamespace(namespace);
    }

    public Map<String, String> getLabels() {
        if (labels == null) {
            labels = new LinkedHashMap<>();
        }
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels == null ? new LinkedHashMap<>() : new LinkedHashMap<>(labels);
    }

    public Map<String, String> getAnnotations() {
        if (annotations == null) {
            annotations = new LinkedHashMap<>();
        }
        return annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations == null ? new LinkedHashMap<>() : new LinkedHashMap<>(annotations);
    }

    public String getRbacProfile() {
        return rbacProfile == null ? "NONE" : rbacProfile;
    }

    public void setRbacProfile(String rbacProfile) {
        this.rbacProfile = rbacProfile == null ? "NONE" : rbacProfile;
    }
}
