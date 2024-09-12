package com.izylife.izykube.dto.cluster;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ClusterDTO {
    private String id;
    private String name;
    private String nameSpace;
    private List<NodeDTO> nodes = new ArrayList<>();
    private List<LinkDTO> links = new ArrayList<>();
    private String diagram;

}
