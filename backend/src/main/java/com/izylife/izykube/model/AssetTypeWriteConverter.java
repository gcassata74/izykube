package com.izylife.izykube.model;

import com.izylife.izykube.enums.AssetType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@WritingConverter
public class AssetTypeWriteConverter implements Converter<AssetType, String> {
    @Override
    public String convert(AssetType source) {
        log.debug("Converting enum to string: {}", source);
        return source.getValue();
    }
}