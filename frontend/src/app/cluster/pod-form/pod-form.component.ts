import { AssetService } from './../../services/asset.service';
import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable, map, of, tap } from 'rxjs';
import { Asset } from '../../model/asset.class';
import { DataService } from '../../services/data.service';
import { DiagramService } from '../../services/diagram.service';
import { ControlContainer, FormBuilder, FormGroup, FormGroupDirective, Validators } from '@angular/forms';

@Component({
  selector: 'app-pod-form',
  templateUrl: './pod-form.component.html',
  styleUrls: ['./pod-form.component.scss'],
  viewProviders: [{ provide: ControlContainer, useExisting: FormGroupDirective }]
})
export class PodFormComponent implements OnInit{

  filteredAssets$!: Observable<any[] | undefined >;
  form!: FormGroup;
  selectedNodeType: string | null = null;
  selectedNodeKey!: string;

  constructor(
    private dataService: DataService,
    private diagramService: DiagramService,
    private assetService: AssetService,
    private store: Store,
    private fb: FormBuilder,
    private parentForm: FormGroupDirective
  ) {}

  ngOnInit(): void {

    this.form = this.fb.group({
      assetId: ['', Validators.required]
    });

    this.diagramService.selectedNode$.subscribe(node => {
      this.selectedNodeType = node?.data?.type || null;
      this.selectedNodeKey = node?.data?.key;
      this.form.addControl('id', this.fb.control( this.selectedNodeKey));
      this.parentForm.form.addControl('podForm', this.form);
      this.setupFilteredAssets(node);
    });
 
  }

  setupFilteredAssets(selectedNode: go.Node | null): void {
    this.filteredAssets$ = this.assetService.getFilterdAssets(selectedNode);
  }

}
