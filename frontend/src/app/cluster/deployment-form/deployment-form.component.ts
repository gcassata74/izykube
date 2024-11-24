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
  providers: [AutoSaveService]
})
export class DeploymentFormComponent implements OnInit {
  @Input() selectedNode!: Deployment;
  form!: FormGroup;
  strategyTypes = [
    { label: 'Recreate', value: 'Recreate' },
    { label: 'Rolling Update', value: 'RollingUpdate' }
  ];

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
      strategyType: [this.selectedNode.strategyType, Validators.required]
    });
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }
}