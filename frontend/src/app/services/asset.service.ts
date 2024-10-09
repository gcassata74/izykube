import { Injectable } from '@angular/core';
import { Observable, map, of } from 'rxjs';
import { Asset } from '../model/asset.class';
import { DiagramService } from './diagram.service';
import { DataService } from './data.service';
import { Node } from '../model/node.class';

@Injectable({
  providedIn: 'root'
})
export class AssetService {

  constructor(
    private dataService: DataService,
    private diagramService: DiagramService,
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
    return this.dataService.get<Asset[]>('/asset/all');
  }

  getAssets(): Observable<Asset[]> {
    return this.getAllAssets().pipe(
      map(assets => assets.map(asset => ({
        ...asset,
        label: `${asset.name} - ${asset.version}`
      })))
    )
  }

}
