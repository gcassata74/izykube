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
import { CrdDefinition, CrdDefinitionSummary } from '../model/crd-definition.class';
import { DataService } from './data.service';

@Injectable({ providedIn: 'root' })
export class CrdService {
  constructor(private dataService: DataService) {}

  list(): Observable<CrdDefinitionSummary[]> {
    return this.dataService.get<CrdDefinitionSummary[]>('/crds');
  }

  listAvailable(): Observable<CrdDefinitionSummary[]> {
    return this.dataService.get<CrdDefinitionSummary[]>('/crds/available');
  }

  get(id: string): Observable<CrdDefinition> {
    return this.dataService.get<CrdDefinition>(`/crds/${id}`);
  }

  getAvailable(id: string): Observable<CrdDefinition> {
    return this.dataService.get<CrdDefinition>(`/crds/available/${encodeURIComponent(id)}`);
  }

  create(payload: Partial<CrdDefinition>): Observable<CrdDefinition> {
    return this.dataService.post<CrdDefinition>('/crds', payload);
  }

  update(id: string, payload: Partial<CrdDefinition>): Observable<CrdDefinition> {
    return this.dataService.put<CrdDefinition>(`/crds/${id}`, payload);
  }

  delete(id: string): Observable<any> {
    return this.dataService.delete<any>(`/crds/${id}`);
  }
}
