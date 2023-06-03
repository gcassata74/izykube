package com.izylife.izykube.services;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DockerService {

    private DockerClient dockerClient;
    private static final Logger log = LoggerFactory.getLogger(DockerService.class);

    @Autowired
    public DockerService(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public String pullImage(String image, String tag) {

        if (tag == null) tag = "latest";
        try {
            log.info("Pulling image {}:{}", image, tag);
            ResultCallback.Adapter<PullResponseItem> pullImage = dockerClient.pullImageCmd(image).withTag(tag).exec(new PullImageResultCallback()).awaitCompletion();
            return String.format("Image %s:$s created successfully!", image, tag);
        } catch (Exception e) {
            log.error("Error pulling image {}:{}", image, tag, e);
            return String.format("Error pulling image %s:%s", image, tag);
        }
    }

    public String createImage(MultipartFile dockerFile, String imageName, String tag) {

        final String finalTag;
        if (tag != null && !tag.isEmpty()) {
            finalTag = tag;
        } else {
            finalTag = "latest";
        }

        try {
            DockerClient dockerClient = DockerClientBuilder.getInstance().build();
            BuildImageResultCallback callback = new BuildImageResultCallback() {
                public void onNext(BuildResponseItem item) {
                    super.onNext(item);
                    log.info(String.format("Created image %s:%s", imageName, finalTag));
                }
            };

            Path tempDirectory = Files.createTempDirectory("docker-build");
            File tempFile = File.createTempFile( dockerFile.getOriginalFilename(), ".tmp",tempDirectory.toFile() );
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(dockerFile.getBytes());
            }

            dockerClient.buildImageCmd(tempFile)
                    .withTag(imageName + ":" + finalTag)
                    .exec(callback)
                    .awaitImageId();

        } catch (Exception e) {
            log.error("Error creating image {}:{}", imageName, tag, e);
            return String.format("Error creating image %s:%s", imageName, tag);
        }

        return String.format("Created image %s:%s", imageName, finalTag);
    }

    public String startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
        return "Container started successfully!";
    }

    public String stopContainer(String containerId) {
        dockerClient.stopContainerCmd(containerId).exec();
        return "Container stopped successfully!";
    }

    public String removeContainer(String containerId) {
        dockerClient.removeContainerCmd(containerId).exec();
        return "Container removed successfully!";
    }

    public String removeImage(String imageId) {
        dockerClient.removeImageCmd(imageId).exec();
        return "Image removed successfully!";
    }

}

