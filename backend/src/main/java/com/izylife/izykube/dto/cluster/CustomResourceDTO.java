package com.izylife.izykube.dto.cluster;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CustomResourceDTO extends NodeDTO {
    private String crdId;
    private String crdGroup;
    private String crdVersion;
    private String crdKind;
    private String crdPlural;
    private String crdScope = "Namespaced";
    private Map<String, Object> spec = new LinkedHashMap<>();

    public CustomResourceDTO(String id, String name) {
        super(id, name, "cr");
    }
}
