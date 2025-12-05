package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ConfigMapDTO extends NodeDTO {

    private String yaml;
    private boolean secret;
    private List<ConfigEntryDTO> entries = new ArrayList<>();
    private Boolean showSecretsAsPlain;

    public ConfigMapDTO(String id, String name, String yaml) {
        this(id, name, yaml, false, "configmap");
    }

    @JsonCreator
    public ConfigMapDTO(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("yaml") String yaml,
            @JsonProperty("secret") Boolean secret
    ) {
        this(id, name, yaml, Boolean.TRUE.equals(secret), "configmap");
    }

    protected ConfigMapDTO(String id, String name, String yaml, boolean secret, String kind) {
        super(id, name, kind);
        this.yaml = yaml;
        this.secret = secret;
    }

    public List<ConfigEntryDTO> getEntries() {
        if (entries == null) {
            entries = new ArrayList<>();
        }
        return entries;
    }

    public void setEntries(List<ConfigEntryDTO> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    @Override
    public void setKind(String kind) {
        super.setKind(kind == null ? "configmap" : kind);
    }
}
