package com.izylife.izykube.dto.cluster;

import lombok.Data;

import java.util.List;

@Data
public class PodSpec extends Base {
    private List<Container> containers;
    private List<Volume> volumes; // This can be optional in your application logic


    public PodSpec(String id, String name, List<Container> containers, List<Volume> volumes) {
        super(id, name); // Initialize Base class fields
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
