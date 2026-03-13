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
import { HttpParams } from '@angular/common/http';
import { DataService } from './data.service';
import { OperatorHubListResponse } from '../model/operator-hub.model';

@Injectable({ providedIn: 'root' })
export class OperatorHubService {
  constructor(private dataService: DataService) {}

  list(query?: string, page = 1, size = 50): Observable<OperatorHubListResponse> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));
    if (query && query.trim().length) {
      params = params.set('q', query.trim());
    }
    return this.dataService.get<OperatorHubListResponse>('/operatorhub/operators', params);
  }

  fetchInstallYaml(name: string): Observable<string> {
    return this.dataService.getText(`/operatorhub/install/${encodeURIComponent(name)}`);
  }
}
