package com.izylife.izykube.dto.cluster;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AccessPolicyDTO extends NodeDTO {

    private List<AccessPolicyRuleDTO> rules = new ArrayList<>();
    private AccessPolicyBindingStrategy targetBindingStrategy = AccessPolicyBindingStrategy.WORKLOAD_SA_PER_WORKLOAD;
    private String existingServiceAccountName;
    private String roleKind = "Role";
    private String bindingKind = "RoleBinding";
    private String rbacNodeType = "ROLE";
    private String subjectServiceAccountName;
    private String roleRefName;
    private String roleRefKind = "Role";

    public AccessPolicyDTO(String id, String name) {
        super(id, name, "accesspolicy");
    }

    @Override
    @JsonProperty("namespace")
    public String getNamespace() {
        return super.getNamespace();
    }

    @Override
    @JsonProperty("namespace")
    public void setNamespace(String namespace) {
        super.setNamespace(namespace);
    }
}
