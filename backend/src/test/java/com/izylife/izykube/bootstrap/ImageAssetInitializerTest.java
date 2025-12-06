package com.izylife.izykube.bootstrap;

import com.izylife.izykube.configuration.ImageAssetProperties;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.ApplicationArguments;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageAssetInitializerTest {

    @Mock
    private AssetRepository assetRepository;

    private ImageAssetProperties properties;
    private ImageAssetInitializer initializer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new ImageAssetProperties();
        properties.setDefaultImages(List.of("nginx:latest", "redis:7"));
        initializer = new ImageAssetInitializer(assetRepository, properties);
    }

    @Test
    void seedsDefaultImagesWhenMissing() throws Exception {
        when(assetRepository.existsByTypeAndImageIgnoreCase(any(), any())).thenReturn(false);

        initializer.run(mock(ApplicationArguments.class));

        verify(assetRepository, times(properties.getDefaultImages().size())).save(any(Asset.class));
    }

    @Test
    void skipsCreationWhenImageAlreadyPresent() throws Exception {
        when(assetRepository.existsByTypeAndImageIgnoreCase(any(), any())).thenReturn(true);

        initializer.run(mock(ApplicationArguments.class));

        verify(assetRepository, never()).save(any());
    }
}
