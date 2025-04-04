package com.izylife.izykube.services.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DockerImageService {

    private DockerClient dockerClient;
    private static final Logger log = LoggerFactory.getLogger(DockerImageService.class);

    @Autowired
    public DockerImageService(DockerClient dockerClient) {
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

    //    public String createImage(MultipartFile dockerFile, String imageName, String tag) {
//
//        final String finalTag;
//        if (tag != null && !tag.isEmpty()) {
//            finalTag = tag;
//        } else {
//            finalTag = "latest";
//        }
//
//        try {
//            DockerClient dockerClient = DockerClientBuilder.getInstance().build();
//            BuildImageResultCallback callback = new BuildImageResultCallback() {
//                public void onNext(BuildResponseItem item) {
//                    super.onNext(item);
//                    log.info(String.format("Created image %s:%s", imageName, finalTag));
//                }
//            };
//
//            Path tempDirectory = Files.createTempDirectory("docker-build");
//            File tempFile = File.createTempFile( dockerFile.getOriginalFilename(), ".tmp",tempDirectory.toFile() );
//            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
//                fos.write(dockerFile.getBytes());
//            }
//
//            dockerClient.buildImageCmd(tempFile)
//                    .withTag(imageName + ":" + finalTag)
//                    .exec(callback)
//                    .awaitImageId();
//
//        } catch (Exception e) {
//            log.error("Error creating image {}:{}", imageName, tag, e);
//            return String.format("Error creating image %s:%s", imageName, tag);
//        }
//
//        return String.format("Created image %s:%s", imageName, finalTag);
//    }
    public String createImage(MultipartFile dockerArchive, String imageName, String tag) {

        final String finalTag;
        if (tag != null && !tag.isEmpty()) {
            finalTag = tag;
        } else {
            finalTag = "latest";
        }

        try {
            DockerClient dockerClient = DockerClientImpl.getInstance();
            BuildImageResultCallback callback = new BuildImageResultCallback() {
                public void onNext(BuildResponseItem item) {
                    super.onNext(item);
                    log.info(String.format("Created image %s:%s", imageName, finalTag));
                }
            };

            // Create a temporary directory
            Path tempDirectory = Files.createTempDirectory("docker-build");

            // Unzip the Docker archive to the temporary directory
            unzipDockerArchive(dockerArchive, tempDirectory);

            // Build the Docker image using the unzipped files
            dockerClient.buildImageCmd(tempDirectory.toFile())
                    .withTag(imageName + ":" + finalTag)
                    .exec(callback)
                    .awaitImageId();

        } catch (Exception e) {
            log.error("Error creating image {}:{}", imageName, tag, e);
            return String.format("Error creating image %s:%s", imageName, tag);
        }

        return String.format("Created image %s:%s", imageName, finalTag);
    }

    public String removeImage(String imageId) {
        dockerClient.removeImageCmd(imageId).exec();
        return "Image removed successfully!";
    }

    private void unzipDockerArchive(MultipartFile dockerArchive, Path destination) throws IOException {
        // Convert MultipartFile to java.io.File
        File archiveFile = new File(destination.toFile(), dockerArchive.getOriginalFilename());
        try (FileOutputStream fos = new FileOutputStream(archiveFile)) {
            fos.write(dockerArchive.getBytes());
        }

        // Extract the archive
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archiveFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File destFile = new File(destination.toFile(), zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    if (!destFile.exists()) {
                        destFile.mkdir();
                    }
                } else {
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }
    }
}

