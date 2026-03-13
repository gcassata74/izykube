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
