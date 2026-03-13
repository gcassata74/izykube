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

export class ServiceAccount extends Node {
  namespace: string;
  automountServiceAccountToken: boolean;
  labels: Record<string, string>;
  annotations: Record<string, string>;
  rbacProfile: 'NONE' | 'VIEW' | 'EDIT' | 'ADMIN';

  constructor(
    id: string,
    name: string,
    namespace: string = 'default',
    automountServiceAccountToken: boolean = true,
    labels: Record<string, string> = {},
    annotations: Record<string, string> = {},
    rbacProfile: 'NONE' | 'VIEW' | 'EDIT' | 'ADMIN' = 'NONE'
  ) {
    super(id, name, 'serviceaccount');
    this.namespace = namespace;
    this.automountServiceAccountToken = automountServiceAccountToken;
    this.labels = labels;
    this.annotations = annotations;
    this.rbacProfile = rbacProfile;
  }
}
