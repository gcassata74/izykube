package com.izylife.izykube.services;

import com.izylife.izykube.dto.cluster.AssetDTO;
import com.izylife.izykube.enums.AssetSource;
import com.izylife.izykube.enums.AssetType;
import com.izylife.izykube.model.Asset;
import com.izylife.izykube.repositories.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssetService {

    private static final int DEFAULT_IMAGE_LIMIT = 50;

    private final AssetRepository assetRepository;

    public List<AssetDTO> findAll() {
        return assetRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<AssetDTO> findImageAssets(String search) {
        return findImageAssets(search, DEFAULT_IMAGE_LIMIT);
    }

    public List<AssetDTO> findImageAssets(String search, int maxResults) {
        List<Asset> images = assetRepository.findByType(AssetType.IMAGE);
        var stream = images.stream();

        if (StringUtils.hasText(search)) {
            final String needle = search.trim().toLowerCase(Locale.ROOT);
            stream = stream.filter(asset -> matchesNeedle(asset, needle));
        }

        Comparator<Asset> comparator = Comparator.comparing(
                (Asset asset) -> Optional.ofNullable(asset.getName())
                        .orElse(Optional.ofNullable(asset.getImage()).orElse("")),
                String.CASE_INSENSITIVE_ORDER
        );

        long limit = maxResults > 0 ? maxResults : images.size();
        return stream
                .sorted(comparator)
                .limit(limit)
                .map(this::toDto)
                .toList();
    }

    public AssetDTO createImageAsset(String name, String imageRef, String description) {
        String normalizedImage = normalizeImageRef(imageRef);
        if (!StringUtils.hasText(normalizedImage)) {
            throw new IllegalArgumentException("Image reference is required");
        }

        // Avoid duplicates by image reference regardless of casing.
        Optional<Asset> existing = assetRepository.findByTypeAndImageIgnoreCase(AssetType.IMAGE, normalizedImage);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        Asset asset = new Asset();
        asset.setName(StringUtils.hasText(name) ? name.trim() : normalizedImage);
        asset.setDescription(description);
        asset.setType(AssetType.IMAGE);
        asset.setImage(normalizedImage);
        asset.setVersion(extractTag(normalizedImage));
        asset.setSource(AssetSource.USER);
        return toDto(assetRepository.save(asset));
    }

    public Asset getAsset(String assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new NoSuchElementException(String.format("Asset not found %s", assetId)));
    }

    public void deleteAsset(String id) {
        try {
            assetRepository.deleteById(id);
        } catch (Exception e) {
            log.error("Error deleting asset: {}", e.getMessage(), e);
        }
    }

    public Asset updateAsset(String id, Asset asset) {
        Asset existingAsset = assetRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(String.format("Asset not found %s", id)));

        existingAsset.setName(asset.getName());
        existingAsset.setDescription(asset.getDescription());
        existingAsset.setImage(asset.getImage());
        existingAsset.setPort(asset.getPort());
        existingAsset.setScript(asset.getScript());
        existingAsset.setType(asset.getType());
        existingAsset.setVersion(asset.getVersion());
        existingAsset.setId(id);

        normalizeAsset(existingAsset);
        return assetRepository.save(existingAsset);
    }

    public Asset createAsset(Asset asset) {
        normalizeAsset(asset);
        return assetRepository.save(asset);
    }

    private void normalizeAsset(Asset asset) {
        if (asset.getType() == AssetType.IMAGE) {
            String normalizedImage = normalizeImageRef(asset.getImage());
            if (!StringUtils.hasText(normalizedImage)) {
                throw new IllegalArgumentException("Image assets must define an image reference");
            }
            asset.setImage(normalizedImage);
            if (!StringUtils.hasText(asset.getVersion())) {
                asset.setVersion(extractTag(normalizedImage));
            }
        }
        if (asset.getSource() == null) {
            asset.setSource(AssetSource.USER);
        }
    }

    private boolean matchesNeedle(Asset asset, String needle) {
        String name = Optional.ofNullable(asset.getName()).orElse("");
        String image = Optional.ofNullable(asset.getImage()).orElse("");
        return name.toLowerCase(Locale.ROOT).contains(needle) ||
                image.toLowerCase(Locale.ROOT).contains(needle);
    }

    private String normalizeImageRef(String imageRef) {
        if (imageRef == null) {
            return null;
        }
        String trimmed = imageRef.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String extractTag(String imageRef) {
        if (!StringUtils.hasText(imageRef)) {
            return "latest";
        }
        int colonIndex = imageRef.lastIndexOf(':');
        if (colonIndex == -1 || colonIndex == imageRef.length() - 1) {
            return "latest";
        }
        return imageRef.substring(colonIndex + 1);
    }

    private AssetDTO toDto(Asset asset) {
        AssetDTO assetDTO = new AssetDTO();
        assetDTO.setId(asset.getId());
        assetDTO.setName(asset.getName());
        assetDTO.setType(asset.getType());
        assetDTO.setDescription(asset.getDescription());
        assetDTO.setImage(asset.getImage());
        assetDTO.setVersion(asset.getVersion());
        assetDTO.setPort(asset.getPort());
        assetDTO.setSource(asset.getSource());
        return assetDTO;
    }
}
