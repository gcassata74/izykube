package com.izylife.izykube.dto.cluster;

import lombok.Data;

@Data
public class LinkDTO {
    private String id;
    private String source;
    private String target;
    private String type;
    private String note;
    private ContainerRole containerRole;
}
