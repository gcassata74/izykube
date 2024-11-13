package com.izylife.izykube.model;

import com.izylife.izykube.enums.AssetType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ReadingConverter
public class AssetTypeReadConverter implements Converter<String, AssetType> {
    @Override
    public AssetType convert(String source) {
        AssetType assetType = null;
        try {
            assetType = AssetType.fromValue(source);
        } catch (IllegalArgumentException e) {
            log.error("Error converting AssetType: " + e.getMessage());
        }
        return assetType;
    }
}

