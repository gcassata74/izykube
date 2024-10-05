import { NotificationService } from 'src/app/services/notification.service';
import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { Cluster } from '../model/cluster.class';
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
}
