import { Observable } from 'rxjs';
import { Cluster } from '../model/cluster.class';
import { DataService } from './data.service';
import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ClusterService {

  constructor(private dataService: DataService) { }


  getAllClusters(): Observable<Cluster[]> {
    return this.dataService.get<Cluster[]>('/cluster/all');
  }
}
