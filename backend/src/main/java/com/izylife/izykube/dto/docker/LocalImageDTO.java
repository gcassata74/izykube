package com.izylife.izykube.dto.docker;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LocalImageDTO {
    private String repository;
    private String tag;
}
