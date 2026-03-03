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
