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
import { HttpParams } from '@angular/common/http';
import { Observable, map, of } from 'rxjs';
import { Asset } from '../model/asset.class';
import { DataService } from './data.service';

@Injectable({
  providedIn: 'root'
})
export class AssetService {

  constructor(
    private dataService: DataService,
  ) {}

  getAsset(id: string): Observable<Asset> {
    return this.dataService.get<Asset>('asset/' + id);
  }

  saveAsset(asset: Asset): Observable<Asset> {
    if (asset.id) {
      return this.dataService.put<Asset>('asset/' + asset.id, asset);
    } else {
      return this.dataService.post<Asset>('asset', asset);
    }
  }

  deleteAsset(id: string): Observable<Asset> {
    return this.dataService.delete<Asset>('/asset/' + id);
  }

  getAllAssets(): Observable<Asset[]> {
    if ((window as any)?.Cypress && Array.isArray((window as any).__izyAssets)) {
      return of((window as any).__izyAssets as Asset[]);
    }
    return this.dataService.get<Asset[]>('/asset/all');
  }

  getControllerAssets(): Observable<Asset[]> {
    const params = new HttpParams().set('type', 'controller');
    return this.dataService.get<Asset[]>('/assets', params);
  }

  getAssets(): Observable<Asset[]> {
    return this.getAllAssets().pipe(
      map(assets => assets.map(asset => ({
        ...asset,
        label: `${asset.name} - ${asset.version}`
      })))
    )
  }

  getImageAssets(search?: string): Observable<Asset[]> {
    let params: HttpParams | undefined;
    if (search && search.trim().length) {
      params = new HttpParams().set('search', search.trim());
    }
    return this.dataService.get<Asset[]>('/image-assets', params);
  }

}
