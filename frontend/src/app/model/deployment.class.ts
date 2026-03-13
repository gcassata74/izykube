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

export type WorkloadType = 'DEPLOYMENT' | 'STATEFULSET' | 'DAEMONSET';

export class Deployment extends Node {
  replicas: number;
  strategyType: 'Recreate' | 'RollingUpdate';
  assetId: string;
  containerPort: number;
  serviceAccountRef?: string | null;
  serviceAccountName?: string | null;
  addToMesh?: boolean;
  override workloadType: WorkloadType;
  command: string[];
  args: string[];

  constructor(
    id: string,
    name: string,
    replicas: number = 1,
    strategyType: 'Recreate' | 'RollingUpdate' = 'RollingUpdate',
    assetId: string = '',
    containerPort: number = 80,
    workloadType: WorkloadType = 'DEPLOYMENT',
    serviceAccountRef: string | null = null,
    serviceAccountName: string | null = null,
    addToMesh: boolean = false,
    command: string[] = [],
    args: string[] = []
  ) {
    super(id, name, 'deployment');
    this.replicas = replicas;
    this.strategyType = strategyType;
    this.assetId = assetId;
    this.containerPort = containerPort;
    this.workloadType = workloadType;
    this.serviceAccountRef = serviceAccountRef;
    this.serviceAccountName = serviceAccountName;
    this.addToMesh = addToMesh;
    this.command = command;
    this.args = args;
  }
}
