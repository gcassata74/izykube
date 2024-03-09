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

  getAssets(): Observable<Asset[]> {
    return this.dataService.get<Asset[]>('asset/all').pipe(
      map(assets => assets.map(asset => ({
        ...asset,
        label: `${asset.name} - ${asset.version}`
      })))
    ) 
  }

}
