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
