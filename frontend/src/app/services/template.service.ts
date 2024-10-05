import { Injectable } from '@angular/core';
import { DataService } from './data.service';
import { Observable } from 'rxjs';
import { Cluster } from '../model/cluster.class';

@Injectable({
  providedIn: 'root'
})
export class TemplateService {

  constructor(
    private dataService: DataService
  ) { }


  createTemplate(selectedId: string): Observable<any> {
    return this.dataService.post('/template/' + selectedId, {});
  }

  updateTemplate(clusterData: Cluster): Observable<any> {
    return this.dataService.put('/template/' + clusterData.id, clusterData);
  }

  deleteTemplate(selectedId: string): Observable<any> {
    return this.dataService.delete('/template/' + selectedId);
  }

}
