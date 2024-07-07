import { Component, Input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { AutoSaveService } from '../../services/auto-save.service';
import { Node } from '../../model/node.class';
import { Deployment } from '../../model/deployment.class';
import { Observable } from 'rxjs';
import { AssetService } from '../../services/asset.service';  // Assume you have this service

@Component({
  selector: 'app-deployment-form',
  templateUrl: './deployment-form.component.html',
  styleUrls: ['./deployment-form.component.scss'],
  providers: [AutoSaveService]
})
export class DeploymentFormComponent implements OnInit {
  @Input() selectedNode!: Node;
  form!: FormGroup;
  filteredAssets$!: Observable<any[]>;

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService,
    private assetService: AssetService  // Inject AssetService
  ) {}

  ngOnInit() {
    this.initForm();
    this.setupAutoSave();
    this.loadAssets();
  }

  private initForm() {
    const deployment = this.selectedNode as Deployment;
    this.form = this.fb.group({
      name: [deployment.name, Validators.required],
      replicas: [deployment.replicas, [Validators.required, Validators.min(1)]],
      assetId: [deployment.assetId, Validators.required],  // Changed from 'image' to 'assetId'
      containerPort: [deployment.containerPort, Validators.required],
      resources: this.fb.group({
        cpu: [deployment.resources.cpu],
        memory: [deployment.resources.memory]
      }),
      envVars: this.fb.array(deployment.envVars.map(env => this.createEnvVarGroup(env)))
    });
  }

  private createEnvVarGroup(env: {name: string, value: string} = {name: '', value: ''}) {
    return this.fb.group({
      name: [env.name, Validators.required],
      value: [env.value, Validators.required]
    });
  }

  private loadAssets() {
    this.filteredAssets$ = this.assetService.getAssets();  // Assume this method exists in AssetService
  }

  emitChange(event: any) {
    // Handle the asset selection change event
    console.log('Selected asset:', event.value);
    // You might want to update other form fields based on the selected asset
  }

  // ... other methods remain the same

  get envVars() {
    return this.form.get('envVars') as FormArray;
  }

  addEnvVar() {
    this.envVars.push(this.createEnvVarGroup());
  }

  removeEnvVar(index: number) {
    this.envVars.removeAt(index);
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }
}