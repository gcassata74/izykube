import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DataService } from './data.service';
import { Namespace } from '../model/namespace.model';
import { ResourceSyncStatus } from '../model/resource-sync-status';
import { Node } from '../model/node.class';

@Injectable({
  providedIn: 'root'
})
export class NamespaceService {

  constructor(private dataService: DataService) {}

  getNamespaces(): Observable<Namespace[]> {
    return this.dataService.get<Namespace[]>('/namespaces');
  }

  createNamespace(namespace: Partial<Namespace>): Observable<Namespace> {
    return this.dataService.post<Namespace>('/namespaces', namespace);
  }

  restartResource(namespace: string, resourceId: string, node: Node): Observable<ResourceSyncStatus> {
    return this.dataService.post<ResourceSyncStatus>(
      `/namespaces/${namespace}/resources/${resourceId}/restart`,
      node
    );
  }
}
