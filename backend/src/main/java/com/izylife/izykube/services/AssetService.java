package com.izylife.izykube.services;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class AssetService {

    @Value("${app.docker.local-repository-uri}")
    private String localRepositoryUri;
    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private DockerClient dockerClient;


    public Asset getAsset(String assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new NoSuchElementException("Asset not found with id: " + assetId));
    }

    public Asset createAsset(Asset asset, MultipartFile dockerfileZip) throws Exception {
        if (dockerfileZip != null && !dockerfileZip.isEmpty()) {
            String imageUri = buildAndPushDockerImage(asset.getName(), asset.getVersion(), dockerfileZip);
            asset.setImage(imageUri);
        }
        return assetRepository.save(asset);
    }

    private String buildAndPushDockerImage(String name, String version, MultipartFile dockerfileZip) throws Exception {

        Path tempDir = Files.createTempDirectory("dockerfile-");

        // Save the Zip File to the Temporary Directory
        Path zipPath = tempDir.resolve("dockerfile.zip");
        dockerfileZip.transferTo(zipPath);

        // Extract the Zip File
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry = zipIn.getNextEntry();
            while (entry != null) {
                Path filePath = tempDir.resolve(entry.getName());
                if (!entry.isDirectory()) {
                    // If the entry is a file, extracts it
                    extractFile(zipIn, filePath);
                } else {
                    // If the entry is a directory, make the directory
                    Files.createDirectories(filePath);
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
        // find the Dockerfile among the extracted files
        Path dockerfilePath = tempDir.resolve("Dockerfile");
        // Build image
        String imageId = dockerClient.buildImageCmd(dockerfilePath.toFile())
                .withTags(Set.of(localRepositoryUri + "/" + name + ":" + version))
                .exec(new BuildImageResultCallback() {
                    @Override
                    public void onNext(BuildResponseItem item) {
                        if(item!=null) {
                            super.onNext(item);
                            System.out.println("" + item);
                        }
                    }
                }).awaitImageId();

        // Push image
        dockerClient.pushImageCmd(localRepositoryUri + "/" + name + ":" + version)
                .start()
                .awaitCompletion();

        // Cleanup temporary Dockerfile
        dockerfilePath.toFile().delete();
        return localRepositoryUri + "/" + name + ":" + version;
    }

    // Helper method to extract a file from the zip
    private static void extractFile(ZipInputStream zipIn, Path filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath.toFile()));
        byte[] bytesIn = new byte[4096];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }
}
