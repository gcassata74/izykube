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

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { DeploymentLogs, IstioGatewayInfo, NamespaceOption, NamespaceSummary, PodLogs, WorkloadHealth } from '../model/kube-summary';
import { KubePod } from '../model/kube-pod';
import { KubePodEvent } from '../model/kube-pod-event';

@Injectable({ providedIn: 'root' })
export class KubeExplorerService {

  private readonly baseUrl = '/api/kube';
  private readonly kubeV1BaseUrl = '/api/v1';

  constructor(private http: HttpClient) {}

  getNamespaces(): Observable<string[]> {
    return this.http.get<NamespaceOption[]>(`${this.baseUrl}/namespaces`).pipe(
      map((namespaces) => namespaces.map((ns) => ns.name).sort())
    );
  }

  getNamespaceSummary(namespace: string): Observable<NamespaceSummary> {
    const params = new HttpParams().set('namespace', namespace || 'all');
    return this.http.get<NamespaceSummary>(`${this.baseUrl}/summary`, { params });
  }

  getIstioGatewayInfo(): Observable<IstioGatewayInfo> {
    return this.http.get<IstioGatewayInfo>(`${this.baseUrl}/istio-gateway`);
  }

  getInternalCaCertificate(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/ca-cert`, { responseType: 'blob' });
  }

  getResourceYaml(kind: string, namespace: string, name: string): Observable<string> {
    return this.http.get(
      `${this.baseUrl}/resources/${encodeURIComponent(kind)}/${encodeURIComponent(namespace)}/${encodeURIComponent(name)}/yaml`,
      { responseType: 'text' }
    );
  }

  updateResourceYaml(kind: string, namespace: string, name: string, yaml: string): Observable<string> {
    return this.http.put(
      `${this.baseUrl}/resources/${encodeURIComponent(kind)}/${encodeURIComponent(namespace)}/${encodeURIComponent(name)}/yaml`,
      yaml,
      { responseType: 'text' }
    );
  }

  getWorkloadHealth(namespace: string): Observable<WorkloadHealth[]> {
    const params = new HttpParams().set('namespace', namespace);
    return this.http.get<WorkloadHealth[]>(`${this.baseUrl}/workloads/health`, { params });
  }

  getWorkloadLogs(kind: string, namespace: string, name: string, tail = 500, previous = false): Observable<DeploymentLogs> {
    const params = new HttpParams()
      .set('kind', kind)
      .set('namespace', namespace)
      .set('name', name)
      .set('tail', tail.toString())
      .set('previous', String(previous));
    return this.http.get<DeploymentLogs>(`${this.baseUrl}/logs/workload`, { params });
  }

  getPodLogs(namespace: string, name: string, tail = 500): Observable<PodLogs> {
    const params = new HttpParams()
      .set('namespace', namespace)
      .set('name', name)
      .set('tail', tail.toString());
    return this.http.get<PodLogs>(`${this.baseUrl}/logs/pod`, { params });
  }

  getDeploymentLogs(namespace: string, name: string, tail = 500): Observable<DeploymentLogs> {
    const params = new HttpParams()
      .set('namespace', namespace)
      .set('name', name)
      .set('tail', tail.toString());
    return this.http.get<DeploymentLogs>(`${this.baseUrl}/logs/deployment`, { params });
  }

  setDeploymentMesh(namespace: string, name: string, enabled: boolean): Observable<void> {
    const params = new HttpParams()
      .set('namespace', namespace)
      .set('enabled', String(enabled));
    return this.http.post<void>(
      `${this.baseUrl}/deployments/${encodeURIComponent(name)}/mesh`,
      {},
      { params }
    );
  }

  getPod(namespace: string, podName: string): Observable<KubePod> {
    return this.http.get<KubePod>(`${this.kubeV1BaseUrl}/namespaces/${encodeURIComponent(namespace)}/pods/${encodeURIComponent(podName)}`);
  }

  getPodLogsV1(namespace: string, podName: string, container?: string, tailLines = 500): Observable<string> {
    let params = new HttpParams().set('tailLines', tailLines.toString());
    if (container) {
      params = params.set('container', container);
    }
    return this.http.get(
      `${this.kubeV1BaseUrl}/namespaces/${encodeURIComponent(namespace)}/pods/${encodeURIComponent(podName)}/log`,
      { params, responseType: 'text' }
    );
  }

  getPodEvents(namespace: string, podName: string): Observable<KubePodEvent[]> {
    const params = new HttpParams().set('fieldSelector', `involvedObject.kind=Pod,involvedObject.name=${podName}`);
    return this.http.get<KubePodEvent[]>(
      `${this.kubeV1BaseUrl}/namespaces/${encodeURIComponent(namespace)}/events`,
      { params }
    );
  }
}
