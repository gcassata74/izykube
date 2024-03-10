import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { Cluster } from '../model/cluster.class';
import { DataService } from './data.service';
import { Injectable } from '@angular/core';
import { updateCluster } from '../store/actions/cluster.actions';
import { Store } from '@ngrx/store';

@Injectable()
export class ClusterService {

  clusterService: any;

  constructor(
    private dataService: DataService,
    private store: Store
    ) { }


  getAllClusters(): Observable<Cluster[]> {
    return this.dataService.get<Cluster[]>('/cluster/all');
  }

  getCluster(id: string): Observable<Cluster> {
    return this.dataService.get<Cluster>('/cluster/'+id);
  }


  deleteCluster(id: string): Observable<Cluster[]> {
    return this.dataService.delete<Cluster>('/cluster/'+id).pipe(
      switchMap(() => this.getAllClusters())
    )
  }


  saveCluster(clusterData: Cluster) {
    let saveObservable: Observable<Cluster>;
  
    if (clusterData.id) {
      // If the ID is present, use PUT to update
      saveObservable = this.dataService.put<Cluster>('cluster/' + clusterData.id, clusterData);
    } else {
      // If the ID is not present, use POST to create
      saveObservable = this.dataService.post<Cluster>('cluster', clusterData);
    }
    saveObservable.pipe(
      catchError((error) => {
        console.error('Error saving cluster', error);
        return throwError(() => error);
      })
    ).subscribe((savedCluster:Cluster) => {
      // Dispatch the update action with the saved cluster data
      this.store.dispatch(updateCluster({ cluster: savedCluster }));
    });
  }
}
