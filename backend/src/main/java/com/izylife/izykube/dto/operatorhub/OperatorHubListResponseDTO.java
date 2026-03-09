package com.izylife.izykube.dto.operatorhub;

import lombok.Data;

import java.util.List;

@Data
public class OperatorHubListResponseDTO {
    private List<OperatorHubOperatorDTO> items;
    private int page;
    private int size;
    private int total;
    private String query;
}
