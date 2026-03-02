import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { DeploymentLogs, IngressClassSummary, IngressGatewayInfo, NamespaceOption, NamespaceSummary, PodLogs } from '../model/kube-summary';
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

  getIngressClasses(): Observable<IngressClassSummary[]> {
    return this.http.get<IngressClassSummary[]>(`${this.baseUrl}/ingress-classes`);
  }

  getIngressGatewayInfo(): Observable<IngressGatewayInfo> {
    return this.http.get<IngressGatewayInfo>(`${this.baseUrl}/ingress-gateway`);
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
