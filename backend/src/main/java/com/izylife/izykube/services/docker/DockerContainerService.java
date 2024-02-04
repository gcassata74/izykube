package com.izylife.izykube.services.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.izylife.izykube.dto.ContainerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DockerContainerService {

    private DockerClient dockerClient;
    private static final Logger log = LoggerFactory.getLogger(DockerContainerService.class);

    @Autowired
    public DockerContainerService(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }


    public String stopContainer(String containerId) {
        dockerClient.stopContainerCmd(containerId).exec();
        return "Container stopped successfully!";
    }

    public String removeContainer(String containerId) {
        dockerClient.removeContainerCmd(containerId).exec();
        return "Container removed successfully!";
    }

    public String runContainer(String imageName, String[] command, String[] volumeBindings) {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        CreateContainerCmd createContainerCmd = dockerClient.createContainerCmd(imageName);

        if (command != null) {
            createContainerCmd.withCmd(command);
        }

        if (volumeBindings != null) {
            for (String binding : volumeBindings) {
                String[] parts = binding.split(":");
                String hostPath = parts[0];
                String containerPath = parts[1];
                createContainerCmd.getHostConfig().withBinds(new Bind(hostPath, new Volume(containerPath)));
            }
        }

        String containerId = createContainerCmd.exec().getId();
        return String.format("Container started with Container ID: %s" ,containerId);
    }

    public List<ContainerInfo> getContainers(boolean includeExited) {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(includeExited)
                .exec();

        List<ContainerInfo> containerInfos = new ArrayList<>();
        for (Container container : containers) {
            ContainerInfo containerInfo = new ContainerInfo(container.getId(), container.getImage(), container.getNames()[0]);
            containerInfos.add(containerInfo);
        }

        return containerInfos;
    }

    public ContainerInfo getContainerById(String containerId, boolean includeExited) {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        InspectContainerResponse containerResponse;
        if (includeExited) {
            containerResponse = dockerClient.inspectContainerCmd(containerId).exec();
        } else {
            List<Container> runningContainers = dockerClient.listContainersCmd()
                    .withShowAll(includeExited)
                    .exec();
            Optional<Container> container = runningContainers.stream()
                    .filter(c -> c.getId().startsWith(containerId))
                    .findFirst();
            if (container.isPresent()) {
                containerResponse = dockerClient.inspectContainerCmd(container.get().getId()).exec();
            } else {
               return null;
            }
        }

        String imageName = containerResponse.getConfig().getImage();
        String containerName = containerResponse.getName().substring(1); // Remove the leading slash

        return new ContainerInfo(containerId, imageName, containerName);
    }

}

