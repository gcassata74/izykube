/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
