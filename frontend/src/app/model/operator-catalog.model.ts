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

export type OperatorInstallStatus =
  | 'NOT_INSTALLED'
  | 'INSTALLING'
  | 'INSTALLED'
  | 'UPGRADING'
  | 'UNINSTALLING'
  | 'DEGRADED';

export type OperatorUninstallPolicy = 'RETAIN_CRDS' | 'DELETE_CRDS_IF_EMPTY' | 'FORCE_DELETE';

export interface ManagedCrdRef {
  group: string;
  version: string;
  plural: string;
  namespaced: boolean;
}

export interface OperatorCatalogEntry {
  id: string;
  name: string;
  packageName: string;
  channel?: string;
  targetNamespace: string;
  desiredVersion: string;
  installedVersion?: string | null;
  uninstallPolicy: OperatorUninstallPolicy;
  status: OperatorInstallStatus;
  lastMessage?: string;
  updatedAt?: string;
  lastActionAt?: string;
  manifestYaml?: string;
  managedCrds: ManagedCrdRef[];
}

export interface OperatorCatalogPayload {
  name: string;
  packageName: string;
  channel?: string;
  targetNamespace: string;
  desiredVersion: string;
  uninstallPolicy: OperatorUninstallPolicy;
  manifestYaml?: string;
  managedCrds: ManagedCrdRef[];
}

export interface OperatorCatalogActionPayload {
  targetVersion?: string;
  force?: boolean;
}
