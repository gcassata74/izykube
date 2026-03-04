import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RouteSummary } from '../model/kube-summary';

export interface RouteCreateRequest {
  namespace: string;
  name: string;
  host?: string;
  path?: string;
  serviceName: string;
  servicePort: number;
  httpsEnabled?: boolean;
}

@Injectable({ providedIn: 'root' })
export class RoutesService {
  private readonly baseUrl = '/api/routes';

  constructor(private http: HttpClient) {}

  createRoute(request: RouteCreateRequest): Observable<RouteSummary> {
    return this.http.post<RouteSummary>(this.baseUrl, request);
  }

  deleteRoute(namespace: string, name: string): Observable<void> {
    const encodedNamespace = encodeURIComponent(namespace);
    const encodedName = encodeURIComponent(name);
    return this.http.delete<void>(`${this.baseUrl}/${encodedNamespace}/${encodedName}`);
  }

  updateRoute(namespace: string, name: string, request: RouteCreateRequest): Observable<RouteSummary> {
    const encodedNamespace = encodeURIComponent(namespace);
    const encodedName = encodeURIComponent(name);
    return this.http.put<RouteSummary>(`${this.baseUrl}/${encodedNamespace}/${encodedName}`, request);
  }
}
