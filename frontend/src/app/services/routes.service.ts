import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IngressSummary } from '../model/kube-summary';

export interface RouteCreateRequest {
  namespace: string;
  name: string;
  host?: string;
  path?: string;
  ingressClassName?: string;
  serviceName: string;
  servicePort: number;
  tlsSecret?: string;
}

@Injectable({ providedIn: 'root' })
export class RoutesService {
  private readonly baseUrl = '/api/routes';

  constructor(private http: HttpClient) {}

  createRoute(request: RouteCreateRequest): Observable<IngressSummary> {
    return this.http.post<IngressSummary>(this.baseUrl, request);
  }

  deleteRoute(namespace: string, name: string): Observable<void> {
    const encodedNamespace = encodeURIComponent(namespace);
    const encodedName = encodeURIComponent(name);
    return this.http.delete<void>(`${this.baseUrl}/${encodedNamespace}/${encodedName}`);
  }

  updateRoute(namespace: string, name: string, request: RouteCreateRequest): Observable<IngressSummary> {
    const encodedNamespace = encodeURIComponent(namespace);
    const encodedName = encodeURIComponent(name);
    return this.http.put<IngressSummary>(`${this.baseUrl}/${encodedNamespace}/${encodedName}`, request);
  }
}
