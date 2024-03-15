import { PodFormComponent } from './../pod-form/pod-form.component';
import { DiagramService } from 'src/app/services/diagram.service';
import { initialState } from './../../store/states/state';
import { AutoSaveService } from './../../services/auto-save.service';
import { Deployment } from './../../model/deployment.class';
import { DiagramComponent } from './../../diagram/diagram.component';
import { Component, EventEmitter, OnDestroy, Output, OnInit, Input, ViewChild } from '@angular/core';
import { FormGroup, FormBuilder, Validators, ControlContainer } from '@angular/forms';
import { SelectItem } from 'primeng/api/selectitem';
import { Observable, Subscription, Subject, switchMap, takeUntil, tap, merge } from 'rxjs';
import { AssetService } from 'src/app/services/asset.service';
import { Node } from '../../model/node.class';
import { getNodeById } from 'src/app/store/selectors/selectors';
import { Store } from '@ngrx/store';

@Component({
  selector: 'app-deployment-form',
  templateUrl: './deployment-form.component.html',
  styleUrls: ['./deployment-form.component.scss'],
  providers: [AutoSaveService]
})
export class DeploymentFormComponent implements OnInit {
  form!: FormGroup;
  @Input() selectedNode!: Node;
  subscription: Subscription = new Subscription();

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService,
  ) { }


  ngOnInit(): void {
   

    const deployment = this.selectedNode as Deployment;
    this.form = this.fb.group({
      replicas: [deployment?.replicas, Validators.required]
    });
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }
}
