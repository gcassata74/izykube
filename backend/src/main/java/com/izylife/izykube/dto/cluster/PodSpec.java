package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class PodSpec extends Base {
    private List<Container> containers;
    private List<Volume> volumes;

    @JsonCreator
    public PodSpec(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("containers") List<Container> containers,
            @JsonProperty("volumes") List<Volume> volumes) {
        super(id, name, "Pod");
        this.containers = containers;
        this.volumes = volumes;
    }

    // Getters and Setters
    public List<Container> getContainers() {
        return containers;
    }

    public void setContainers(List<Container> containers) {
        this.containers = containers;
    }

    public List<Volume> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<Volume> volumes) {
        this.volumes = volumes;
    }
}
