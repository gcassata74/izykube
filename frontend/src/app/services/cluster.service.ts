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

import { NotificationService } from 'src/app/services/notification.service';
import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { Cluster } from '../model/cluster.class';
import { ClusterVersion } from '../model/cluster-version.model';
import { DataService } from './data.service';
import { Injectable } from '@angular/core';
import { updateCluster } from '../store/actions/actions';
import { Store } from '@ngrx/store';
import { Router } from '@angular/router';

@Injectable()
export class ClusterService {


  constructor(
    private dataService: DataService,
    private store: Store,
    private router: Router
  ) {}

  getAllClusters(): Observable<Cluster[]> {
    return this.dataService.get<Cluster[]>('/cluster/all');
  }

  getCluster(clusterId: string): Observable<Cluster> {
    return this.dataService.get<Cluster>('/cluster/' + clusterId);
  }

  deleteCluster(clusterId: string): Observable<Cluster> {
    return this.dataService.delete<Cluster>('/cluster/' + clusterId);
  }

  saveCluster(clusterData: Cluster): Observable<Cluster> {
    if (clusterData.id) {
      return this.dataService.put<Cluster>('cluster/' + clusterData.id, clusterData);
    } else {
      return this.dataService.post<Cluster>('cluster', clusterData);
    }
  }

  patchCluster(id: string | null, clusterData: Cluster) {
    return this.dataService.patch<Cluster>('cluster/' + clusterData.id, clusterData);
  }

  deploy(clusterId: string): Observable<any> {
    return this.dataService.post('/cluster/' + clusterId + '/deploy', {});
  }

  undeploy(clusterId: string): Observable<any> {
    return this.dataService.delete('/cluster/' + clusterId + '/undeploy');
  }

  getNamespaceVersions(namespace: string): Observable<ClusterVersion[]> {
    return this.dataService.get<ClusterVersion[]>(`/cluster/namespace/${encodeURIComponent(namespace)}/versions`);
  }

  getNamespaceVersion(namespace: string, versionNumber: number): Observable<ClusterVersion> {
    return this.dataService.get<ClusterVersion>(`/cluster/namespace/${encodeURIComponent(namespace)}/versions/${versionNumber}`);
  }

  getLatestNamespaceVersion(namespace: string): Observable<ClusterVersion> {
    return this.dataService.get<ClusterVersion>(`/cluster/namespace/${encodeURIComponent(namespace)}/versions/latest`);
  }

  deleteNamespaceVersion(namespace: string, versionNumber: number): Observable<any> {
    return this.dataService.delete(`/cluster/namespace/${encodeURIComponent(namespace)}/versions/${versionNumber}`);
  }

  deleteNamespaceVersionById(versionId: string): Observable<any> {
    return this.dataService.delete(`/cluster/versions/${encodeURIComponent(versionId)}`);
  }
}
