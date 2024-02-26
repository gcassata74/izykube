import { Injectable } from '@angular/core';
import { Observable, map, of } from 'rxjs';
import { Asset } from '../model/asset.class';
import { DiagramService } from './diagram.service';
import { DataService } from './data.service';

@Injectable({
  providedIn: 'root'
})
export class AssetService {


  constructor(
    private dataService: DataService,
    private diagramService: DiagramService,
  ) {}

  getFilterdAssets(selectedNode: go.Node | null): Observable<Asset[]> {
    return selectedNode ? this.dataService.get<Asset[]>('asset/all').pipe(
      map(assets => assets.map(asset => ({
        ...asset,
        label: `${asset.name} - ${asset.version}`
      })))
    ) : of([]);
  }




}
