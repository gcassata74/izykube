import { Observable, switchMap } from 'rxjs';
import { Cluster } from '../model/cluster.class';
import { DataService } from './data.service';
import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ClusterService {
  clusterService: any;

  constructor(private dataService: DataService) { }


  getAllClusters(): Observable<Cluster[]> {
    return this.dataService.get<Cluster[]>('/cluster/all');
  }


  deleteCluster(id: string): Observable<Cluster[]> {
    return this.dataService.delete<Cluster>('/cluster/'+id).pipe(
      switchMap(() => this.getAllClusters())
    )
  }
}
