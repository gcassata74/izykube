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
import { Observable } from 'rxjs';
import { DataService } from './data.service';

export interface PortForwardRequest {
  namespace: string;
  serviceName: string;
  localPort: number;
  targetPort: number;
}

export interface PortForwardResponse {
  namespace: string;
  serviceName: string;
  localPort: number;
  targetPort: number;
  active: boolean;
  message?: string;
}

export interface PortAvailabilityResponse {
  port: number;
  available: boolean;
  message?: string;
}

@Injectable({
  providedIn: 'root'
})
export class PortForwardService {
  constructor(private dataService: DataService) {}

  startForward(request: PortForwardRequest): Observable<PortForwardResponse> {
    return this.dataService.post<PortForwardResponse>('port-forward/start', request);
  }

  stopForward(request: PortForwardRequest): Observable<PortForwardResponse> {
    return this.dataService.post<PortForwardResponse>('port-forward/stop', request);
  }

  listActiveForwards(): Observable<PortForwardResponse[]> {
    return this.dataService.get<PortForwardResponse[]>('port-forward/active');
  }

  listForwards(): Observable<PortForwardResponse[]> {
    return this.dataService.get<PortForwardResponse[]>('port-forward/entries');
  }

  getStatus(namespace: string, serviceName: string, targetPort: number): Observable<PortForwardResponse> {
    const query = `port-forward/status?namespace=${encodeURIComponent(namespace)}&serviceName=${encodeURIComponent(serviceName)}&targetPort=${targetPort}`;
    return this.dataService.get<PortForwardResponse>(query);
  }

  deleteForward(request: PortForwardRequest): Observable<void> {
    return this.dataService.post<void>('port-forward/delete', request);
  }

  checkLocalPort(port: number): Observable<PortAvailabilityResponse> {
    return this.dataService.get<PortAvailabilityResponse>(`port-forward/check?port=${port}`);
  }
}
