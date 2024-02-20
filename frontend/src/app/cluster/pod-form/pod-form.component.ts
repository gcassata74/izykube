import { AssetService } from './../../services/asset.service';
import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable, map, of } from 'rxjs';
import { Asset } from '../../model/asset.class';
import { DataService } from '../../services/data.service';
import { DiagramService } from '../../services/diagram.service';
import { updateNodeAsset } from '../../store/actions/cluster.actions';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-pod-form',
  templateUrl: './pod-form.component.html',
  styleUrls: ['./pod-form.component.scss']
})
export class PodFormComponent implements OnInit{

  filteredAssets$!: Observable<any[] | undefined >;
  @Output() formReady: EventEmitter<FormGroup> = new EventEmitter<FormGroup>();
  podForm!: FormGroup;
  selectedNodeType: string | null = null;
  selectedNodeKey!: string;

  constructor(
    private dataService: DataService,
    private diagramService: DiagramService,
    private assetService: AssetService,
    private store: Store,
    private formBuilder: FormBuilder
  ) {}

  ngOnInit(): void {
    this.diagramService.selectedNode$.subscribe(node => {
      this.selectedNodeType = node?.data?.type || null;
      this.selectedNodeKey = node?.data?.key;
      this.setupFilteredAssets(node);
    });

    this.podForm = this.formBuilder.group({
      asset: ['', Validators.required]
    });
  
    // Emetti l'evento con il form quando è pronto
    this.formReady.emit(this.podForm);

  }

  setupFilteredAssets(selectedNode: go.Node | null): void {
    this.filteredAssets$ = this.assetService.getFilterdAssets(selectedNode);
  }

  // onAssetSelected(asset: Asset): void {
  //   // Dispatch the action to update the node's assetId
  //   this.store.dispatch(updateNodeAsset({ nodeId: this.selectedNodeKey, assetId: asset.id }));
  // }



}
