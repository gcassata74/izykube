import { Component, OnInit } from '@angular/core';
import { DataService } from '../../services/data.service';
import { Asset } from '../../model/asset.class';
import { map, tap, filter, Observable, switchMap, of, startWith } from 'rxjs';
import { DiagramService } from 'src/app/services/diagram.service';

@Component({
  selector: 'app-asset-select',
  templateUrl: './asset-select.component.html',
  styleUrls: ['./asset-select.component.scss']
})
export class AssetSelectComponent implements OnInit {
  assets: Asset[] = [];
  selectedAsset: any;
  filteredAssets$!: Observable<any[] | undefined >;

  constructor(
    private dataService: DataService,
    private diagramService: DiagramService
  ) {}

  ngOnInit(): void {
    this.filteredAssets$ = this.diagramService.selectedNode$.pipe(
      switchMap((selectedNode: go.Node | null) => {
        if (selectedNode) {
          return this.dataService.get<Asset[]>('asset/all').pipe(
            map(assets => assets.filter(asset => asset.type === selectedNode.data.type)),
            map(assets => assets.map(asset => ({
              ...asset,
              label: `${asset.name} - ${asset.version}` // Format label
            })))
          )
        } else {
          return of([]);
        }
      })
    );
  }


}