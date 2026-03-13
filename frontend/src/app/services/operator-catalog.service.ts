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
import {
  OperatorCatalogActionPayload,
  OperatorCatalogEntry,
  OperatorCatalogPayload,
} from '../model/operator-catalog.model';
import { DataService } from './data.service';

@Injectable({ providedIn: 'root' })
export class OperatorCatalogService {
  constructor(private dataService: DataService) {}

  list(): Observable<OperatorCatalogEntry[]> {
    return this.dataService.get<OperatorCatalogEntry[]>('/operator-catalog');
  }

  create(payload: OperatorCatalogPayload): Observable<OperatorCatalogEntry> {
    return this.dataService.post<OperatorCatalogEntry>('/operator-catalog', payload);
  }

  update(id: string, payload: OperatorCatalogPayload): Observable<OperatorCatalogEntry> {
    return this.dataService.put<OperatorCatalogEntry>(`/operator-catalog/${id}`, payload);
  }

  delete(id: string): Observable<{ message: string }> {
    return this.dataService.delete<{ message: string }>(`/operator-catalog/${id}`);
  }

  install(id: string, payload?: OperatorCatalogActionPayload): Observable<OperatorCatalogEntry> {
    return this.dataService.post<OperatorCatalogEntry>(`/operator-catalog/${id}/install`, payload || {});
  }

  upgrade(id: string, payload?: OperatorCatalogActionPayload): Observable<OperatorCatalogEntry> {
    return this.dataService.post<OperatorCatalogEntry>(`/operator-catalog/${id}/upgrade`, payload || {});
  }

  uninstall(id: string, payload?: OperatorCatalogActionPayload): Observable<OperatorCatalogEntry> {
    return this.dataService.post<OperatorCatalogEntry>(`/operator-catalog/${id}/uninstall`, payload || {});
  }
}
