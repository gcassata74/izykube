package com.izylife.izykube.dto.cluster;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class AccessPolicyRuleDTO {
    private List<String> apiGroups = new ArrayList<>(List.of(""));
    private List<String> resources = new ArrayList<>();
    private List<String> verbs = new ArrayList<>();
    private List<String> resourceNames;
}

