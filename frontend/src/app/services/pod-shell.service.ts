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
import { Observable } from 'rxjs';
import { PodSummary } from '../model/kube-summary';

@Injectable({
  providedIn: 'root'
})
export class PodShellService {

  private readonly baseUrl = '/api/kube';

  constructor(private http: HttpClient) {}

  getPodsByDeployment(namespace: string, deploymentName: string): Observable<PodSummary[]> {
    const params = new HttpParams().set('namespace', namespace);
    const encodedDeployment = encodeURIComponent(deploymentName);
    return this.http.get<PodSummary[]>(`${this.baseUrl}/deployments/${encodedDeployment}/pods`, { params });
  }

  createShellSocket(namespace: string, podName: string, containerName?: string): WebSocket {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const host = window.location.host;
    const query = new URLSearchParams({ namespace, pod: podName });
    if (containerName) {
      query.set('container', containerName);
    }

    const socketUrl = `${protocol}://${host}/ws/pod-shell?${query.toString()}`;
    return new WebSocket(socketUrl);
  }
}
