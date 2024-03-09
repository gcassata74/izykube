import { DropdownChangeEvent, DropdownModule } from 'primeng/dropdown';
import { Dropdown } from 'primeng/dropdown';
import { AutoSaveService } from './../../services/auto-save.service';
import { Pod } from './../../model/pod.class';
import { AssetService } from './../../services/asset.service';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable, map, of, tap, Subscription, switchMap, Subject, distinctUntilChanged } from 'rxjs';
import { Asset } from '../../model/asset.class';
import { DataService } from '../../services/data.service';
import { DiagramService } from '../../services/diagram.service';
import { ControlContainer, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Node } from '../../model/node.class';
import { Deployment } from 'src/app/model/deployment.class';

@Component({
  selector: 'app-pod-form',
  templateUrl: './pod-form.component.html',
  styleUrls: ['./pod-form.component.scss'],
  providers: [AutoSaveService]
})
export class PodFormComponent implements OnInit {


  filteredAssets$!: Observable<any[] | undefined>;
  form!: FormGroup;
  @Input() selectedNode!: Node
  @ViewChild("assets") dropDown!: Dropdown;
  _change: Subject<any> = new Subject<any>();
  change$ = this._change.asObservable();
  

  constructor(
    private assetService: AssetService,
    private autoSaveService: AutoSaveService,
    private fb: FormBuilder,
  ) { }

  ngOnInit(): void {
    const pod = this.selectedNode as Pod;
    this.filteredAssets$ = this.assetService.getAssets();
    this.form = this.fb.group({
      assetId: [pod.assetId, Validators.required]
    });
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.change$);
  }


  emitChange(event: DropdownChangeEvent) {
    this._change.next({assetId:event.value});
  }

}
