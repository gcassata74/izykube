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
