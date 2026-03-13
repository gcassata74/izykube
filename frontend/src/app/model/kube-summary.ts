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

export interface PodSummary {
  name: string;
  namespace: string;
  status: string;
  ready: string;
  restarts: number;
  node: string;
  age: string;
}

export interface DeploymentSummary {
  name: string;
  namespace: string;
  readyReplicas: number;
  replicas: number;
  updatedReplicas: number;
  availableReplicas: number;
  age: string;
}

export interface ServiceSummary {
  name: string;
  namespace: string;
  type: string;
  clusterIp: string;
  externalIp: string;
  ports: string;
  age: string;
}

export interface RouteSummary {
  name: string;
  namespace: string;
  hosts: string;
  serviceTargets: string;
  gatewayName: string;
  path: string;
  tls: string;
  age: string;
  status: string;
}

export interface IstioGatewayInfo {
  host: string;
  httpPort?: number;
  httpsPort?: number;
  loadBalancer: boolean;
}

export interface WorkloadHealth {
  kind: string;
  name: string;
  namespace: string;
  unhealthy: boolean;
  reason?: string;
}

export interface ConfigMapSummary {
  name: string;
  namespace: string;
  dataEntries: number;
  age: string;
}

export interface SecretSummary {
  name: string;
  namespace: string;
  type: string;
  dataEntries: number;
  age: string;
}

export interface JobSummary {
  name: string;
  namespace: string;
  completions?: number;
  succeeded?: number;
  failed?: number;
  active?: number;
  age: string;
}

export interface CronJobSummary {
  name: string;
  namespace: string;
  schedule: string;
  suspended: boolean;
  lastScheduleTime: string;
  activeJobs: number;
  age: string;
}

export interface DaemonSetSummary {
  name: string;
  namespace: string;
  desired?: number;
  current?: number;
  ready?: number;
  available?: number;
  updated?: number;
  age: string;
}

export interface StatefulSetSummary {
  name: string;
  namespace: string;
  readyReplicas?: number;
  replicas?: number;
  updatedReplicas?: number;
  age: string;
}

export interface NamespaceSummary {
  namespace: string;
  pods: PodSummary[];
  deployments: DeploymentSummary[];
  services: ServiceSummary[];
  routes: RouteSummary[];
  configMaps: ConfigMapSummary[];
  secrets: SecretSummary[];
  jobs: JobSummary[];
  cronJobs: CronJobSummary[];
  daemonSets: DaemonSetSummary[];
  statefulSets: StatefulSetSummary[];
}

export interface NamespaceOption {
  name: string;
}

export interface PodLogs {
  name: string;
  namespace: string;
  logs: string;
}

export interface DeploymentLogs {
  name: string;
  namespace: string;
  pods: PodLogs[];
}
