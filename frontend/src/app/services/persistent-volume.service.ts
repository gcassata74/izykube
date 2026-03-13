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
import { PersistentVolume } from '../model/persistent-volume.class';
import { DataService } from './data.service';

@Injectable({
  providedIn: 'root'
})
export class PersistentVolumeService {

  constructor(private dataService: DataService) {}

  getVolumes(): Observable<PersistentVolume[]> {
    return this.dataService.get<PersistentVolume[]>('/persistent-volumes');
  }

  getVolume(name: string): Observable<PersistentVolume> {
    return this.dataService.get<PersistentVolume>(`/persistent-volumes/${name}`);
  }

  createVolume(request: PersistentVolume): Observable<PersistentVolume> {
    return this.dataService.post<PersistentVolume>('/persistent-volumes', request);
  }

  updateVolume(name: string, request: PersistentVolume): Observable<PersistentVolume> {
    return this.dataService.put<PersistentVolume>(`/persistent-volumes/${name}`, request);
  }

  deleteVolume(name: string): Observable<void> {
    return this.dataService.delete<void>(`/persistent-volumes/${name}`);
  }
}
