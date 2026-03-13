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
