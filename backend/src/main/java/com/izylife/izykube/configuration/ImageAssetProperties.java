package com.izylife.izykube.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.assets")
public class ImageAssetProperties {

    private List<String> defaultImages = new ArrayList<>();

    public List<String> getDefaultImages() {
        return Collections.unmodifiableList(defaultImages);
    }

    public void setDefaultImages(List<String> defaultImages) {
        if (defaultImages == null) {
            this.defaultImages = new ArrayList<>();
            return;
        }
        this.defaultImages = new ArrayList<>(defaultImages);
    }
}
