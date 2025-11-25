package com.izylife.izykube.web.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImageAssetRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String imageRef;

    private String description;
}
