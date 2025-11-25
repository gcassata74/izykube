package com.izylife.izykube.bootstrap;

import com.izylife.izykube.enums.AssetSource;
import com.izylife.izykube.enums.AssetType;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageAssetInitializer implements ApplicationRunner {

    static final List<String> DEFAULT_IMAGE_REFS = List.of(
            "nginx:latest",
            "nginx:1.27",
            "alpine:3.18",
            "busybox:1.36",
            "redis:7",
            "postgres:16",
            "mysql:8",
            "mongo:7",
            "node:22",
            "openjdk:21",
            "python:3.12",
            "golang:1.22",
            "debian:12",
            "ubuntu:22.04",
            "traefik:3.0",
            "haproxy:2.8",
            "memcached:1.6",
            "rabbitmq:3.12"
    );

    private final AssetRepository assetRepository;

    @Override
    public void run(ApplicationArguments args) {
        DEFAULT_IMAGE_REFS.forEach(this::upsertImageAsset);
    }

    private void upsertImageAsset(String imageRef) {
        boolean exists = assetRepository.existsByTypeAndImageIgnoreCase(AssetType.IMAGE, imageRef);
        if (exists) {
            return;
        }

        Asset asset = new Asset();
        asset.setName(imageRef);
        asset.setType(AssetType.IMAGE);
        asset.setImage(imageRef);
        asset.setVersion(extractTag(imageRef));
        asset.setSource(AssetSource.BUILT_IN);
        assetRepository.save(asset);
        log.info("Registered built-in image asset {}", imageRef);
    }

    private String extractTag(String imageRef) {
        int idx = imageRef.lastIndexOf(':');
        if (idx == -1 || idx == imageRef.length() - 1) {
            return "latest";
        }
        return imageRef.substring(idx + 1);
    }
}
