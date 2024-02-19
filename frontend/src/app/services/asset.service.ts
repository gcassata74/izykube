import { Injectable } from '@angular/core';
import { Observable, map, of } from 'rxjs';
import { Asset } from '../model/asset.class';
import { DiagramService } from './diagram.service';
import { DataService } from './data.service';

@Injectable({
  providedIn: 'root'
})
export class AssetService {

 nodeAssetTypeMapping: { [nodeType: string]: string[] } = {
    'pod': ['container'], 
    'container': ['container'],
    'deployment': ['container']
  };

  constructor(
    private dataService: DataService,
    private diagramService: DiagramService,
  ) {}

  getFilterdAssets(selectedNode: go.Node | null): Observable<Asset[]> {
    return selectedNode ? this.dataService.get<Asset[]>('asset/all').pipe(
      map(assets => assets.filter(asset => {
        const allowedAssetTypes = this.nodeAssetTypeMapping[selectedNode.data.type] || [selectedNode.data.type];
        return allowedAssetTypes.includes(asset.type);
      })),
      map(assets => assets.map(asset => ({
        ...asset,
        label: `${asset.name} - ${asset.version}`
      })))
    ) : of([]);
  }




}
