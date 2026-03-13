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

import { Node } from './node.class';

export type AccessPolicyBindingStrategy =
  | 'WORKLOAD_SA_PER_WORKLOAD'
  | 'WORKLOAD_SA_PER_POLICY'
  | 'WORKLOAD_SA_EXPLICIT_REFERENCE';

export type AccessPolicyRoleKind = 'Role' | 'ClusterRole';
export type AccessPolicyBindingKind = 'RoleBinding' | 'ClusterRoleBinding';
export type AccessPolicyNodeType = 'ROLE' | 'ROLEBINDING';

export interface AccessPolicyRule {
  apiGroups: string[];
  resources: string[];
  verbs: string[];
  resourceNames?: string[];
}

export class AccessPolicy extends Node {
  namespace: string;
  rules: AccessPolicyRule[];
  targetBindingStrategy: AccessPolicyBindingStrategy;
  existingServiceAccountName?: string | null;
  roleKind: AccessPolicyRoleKind;
  bindingKind: AccessPolicyBindingKind;
  rbacNodeType: AccessPolicyNodeType;
  subjectServiceAccountName?: string | null;
  roleRefName?: string | null;
  roleRefKind?: AccessPolicyRoleKind;

  constructor(
    id: string,
    name: string,
    namespace: string = 'default',
    rules: AccessPolicyRule[] = [],
    targetBindingStrategy: AccessPolicyBindingStrategy = 'WORKLOAD_SA_PER_WORKLOAD',
    existingServiceAccountName: string | null = null,
    roleKind: AccessPolicyRoleKind = 'Role',
    bindingKind: AccessPolicyBindingKind = 'RoleBinding',
    rbacNodeType: AccessPolicyNodeType = 'ROLE',
    subjectServiceAccountName: string | null = null,
    roleRefName: string | null = null,
    roleRefKind: AccessPolicyRoleKind = 'Role'
  ) {
    super(id, name, 'accesspolicy');
    this.namespace = namespace;
    this.rules = rules;
    this.targetBindingStrategy = targetBindingStrategy;
    this.existingServiceAccountName = existingServiceAccountName;
    this.roleKind = roleKind;
    this.bindingKind = bindingKind;
    this.rbacNodeType = rbacNodeType;
    this.subjectServiceAccountName = subjectServiceAccountName;
    this.roleRefName = roleRefName;
    this.roleRefKind = roleRefKind;
  }
}
