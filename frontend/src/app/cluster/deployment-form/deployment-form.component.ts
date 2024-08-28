import { Component, Input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AutoSaveService } from '../../services/auto-save.service';
import { Node } from '../../model/node.class';
import { Deployment } from '../../model/deployment.class';
import { Observable } from 'rxjs';
import { AssetService } from '../../services/asset.service';

@Component({
  selector: 'app-deployment-form',
  templateUrl: './deployment-form.component.html',
  styleUrls: ['./deployment-form.component.scss'],
  providers: [AutoSaveService]
})
export class DeploymentFormComponent implements OnInit {
  @Input() selectedNode!: Deployment;
  form!: FormGroup;
  strategyTypes: ('Recreate' | 'RollingUpdate')[] = ['Recreate', 'RollingUpdate'];

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService
  ) {}

  ngOnInit() {
    this.initForm();
    this.setupAutoSave();
  }

  private initForm() {
    this.form = this.fb.group({
      name: [this.selectedNode.name, Validators.required],
      replicas: [this.selectedNode.replicas, [Validators.required, Validators.min(0)]],
      strategy: this.fb.group({
        type: [this.selectedNode.strategy.type, Validators.required],
        rollingUpdate: this.fb.group({
          maxUnavailable: [this.selectedNode.strategy.rollingUpdate?.maxUnavailable],
          maxSurge: [this.selectedNode.strategy.rollingUpdate?.maxSurge]
        })
      }),
      minReadySeconds: [this.selectedNode.minReadySeconds],
      revisionHistoryLimit: [this.selectedNode.revisionHistoryLimit],
      progressDeadlineSeconds: [this.selectedNode.progressDeadlineSeconds]
    });

    // Disable rollingUpdate fields if strategy is not RollingUpdate
    this.form.get('strategy.type')?.valueChanges.subscribe(value => {
      if (value === 'RollingUpdate') {
        this.form.get('strategy.rollingUpdate')?.enable();
      } else {
        this.form.get('strategy.rollingUpdate')?.disable();
      }
    });
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }
}