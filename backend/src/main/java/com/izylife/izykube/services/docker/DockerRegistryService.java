package com.izylife.izykube.services.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.core.command.PushImageResultCallback;
import org.springframework.stereotype.Service;

@Service
public class DockerRegistryService {

    private DockerClient dockerClient;

    public DockerRegistryService(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public String pushImageToLocalRegistry(String imageName, String tag){
        if (tag == null || tag.isEmpty()) tag = "latest";
        try {
            // Tag the image for the local registry
            String taggedImageName = "localhost:5000/" + imageName + ":" + tag;
            dockerClient.tagImageCmd(imageName, taggedImageName, tag).exec();

            AuthConfig config = new AuthConfig()
                    .withRegistryAddress("localhost:5000");

            // Push the image to the local registry
            dockerClient.pushImageCmd(taggedImageName)
                    .withAuthConfig(config)
                    .exec(new PushImageResultCallback())
                    .awaitCompletion();

            return String.format("Image %s pushed to the local Docker registry", imageName);
        } catch (Exception e) {
            return "Failed to push the image to the local Docker registry: " + e.getMessage();
        }
    }

    public String deleteImageFromLocalRegistry(String imageName) throws DockerException, InterruptedException {
        dockerClient.removeImageCmd(imageName).exec();
        return String.format("Image %s removed from the local Docker registry",imageName);
    }

}
