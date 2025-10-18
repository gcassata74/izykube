import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { Asset } from '../model/asset.class';
import { DiagramService } from './diagram.service';
import { DataService } from './data.service';
import { Node } from '../model/node.class';

export interface DockerImageOption {
  repository: string;
  tag: string;
  label: string;
  value: string;
}

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

  getLocalDockerImages(): Observable<DockerImageOption[]> {
    return this.dataService.get<{ repository: string; tag: string; }[]>('docker/image/local').pipe(
      map(images => {
        const seen = new Set<string>();
        return images
          .map(image => ({
            repository: image.repository,
            tag: image.tag,
            label: `${image.repository}:${image.tag}`,
            value: `${image.repository}:${image.tag}`
          }))
          .filter(image => {
            if (seen.has(image.value)) {
              return false;
            }
            seen.add(image.value);
            return true;
          });
      })
    );
  }

}
