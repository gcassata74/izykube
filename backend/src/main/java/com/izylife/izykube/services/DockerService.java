package com.izylife.izykube.services;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/************************************************************************
 * Created: 12/07/22                                                     *
 * Author: Giuseppe Cassata                                              *
 ************************************************************************/
@Service
@Slf4j
@RequiredArgsConstructor
public class DockerService {

  @NonNull
  DockerClientConfig config;

  @NonNull
  DockerHttpClient httpClient;

  public void pull(String image, String tag) {
    if (tag == null) tag = "latest";
    DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
    try {
      log.info("Pulling image {}:{}", image, tag);
      ResultCallback.Adapter<PullResponseItem> pullImage = dockerClient.pullImageCmd(image).withTag(tag).exec(new PullImageResultCallback()).awaitCompletion();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public CreateContainerResponse run(String image) {
    log.info("Creating container for image {}", image);
    DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
    CreateContainerResponse container =
      dockerClient
        .createContainerCmd(image)
        .withTty(false)
        .withStdinOpen(true)
        .exec();
    log.info("Staring container {} for image {}", container.getId(), image);
    dockerClient.startContainerCmd(container.getId()).exec();
    return container;
  }

  public InspectContainerResponse inspect(String containerId) {
    log.info("Inspecting container: {}", containerId);
    DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
    InspectContainerResponse inspectContainer = dockerClient.inspectContainerCmd(containerId).exec();
    return inspectContainer;
  }

  public void stop(String containerId) {
    log.info("Stopping container: {}", containerId);
    DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
    dockerClient.stopContainerCmd(containerId).exec();
    log.info("Container {} stopped!" , containerId );
  }

  public List<Container> list() {
    log.info("Listing containers");
    DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
    return dockerClient.listContainersCmd().withShowAll(true).exec();
  }

  public void deleteContainer(String containerId) {
    log.info("Deleting container: {}",containerId);
    DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
    dockerClient.removeContainerCmd(containerId).exec();
    log.info("container {} deleted",containerId);
  }

  public void deleteImage(String image) {
    log.info("Deleting image: {}",image);
    DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
    dockerClient.removeImageCmd(image).exec();
    log.info("Image {} deleted",image);
  }

  public String createImageFromDockerfile(String path) throws Exception{

    DockerClient dockerClient = DockerClientBuilder.getInstance().build();
    BuildImageResultCallback callback = new BuildImageResultCallback() {
      public void onNext(BuildResponseItem item) {
        System.out.println("" + item);
      }
    };
    File dockerFile = new File(path);
    return  dockerClient.buildImageCmd(dockerFile).exec(callback).awaitImageId();

  }

}
