import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { DeploymentLogs, NamespaceOption, NamespaceSummary, PodLogs } from '../model/kube-summary';

@Injectable({ providedIn: 'root' })
export class KubeExplorerService {

  private readonly baseUrl = '/api/kube';

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
}
