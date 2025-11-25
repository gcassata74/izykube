package com.izylife.izykube.bootstrap;

import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageAssetInitializerTest {

    @Mock
    private AssetRepository assetRepository;

    private ImageAssetInitializer initializer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        initializer = new ImageAssetInitializer(assetRepository);
    }

    @Test
    void seedsDefaultImagesWhenMissing() throws Exception {
        when(assetRepository.existsByTypeAndImageIgnoreCase(any(), any())).thenReturn(false);

        initializer.run(mock(ApplicationArguments.class));

        verify(assetRepository, times(ImageAssetInitializer.DEFAULT_IMAGE_REFS.size())).save(any(Asset.class));
    }

    @Test
    void skipsCreationWhenImageAlreadyPresent() throws Exception {
        when(assetRepository.existsByTypeAndImageIgnoreCase(any(), any())).thenReturn(true);

        initializer.run(mock(ApplicationArguments.class));

        verify(assetRepository, never()).save(any());
    }
}
