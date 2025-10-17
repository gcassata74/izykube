import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { NamespaceOption, NamespaceSummary } from '../model/kube-summary';

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
}
