import { Component, Input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { catchError, map, Observable, of } from 'rxjs';
import { AutoSaveService } from '../../services/auto-save.service';
import { Deployment } from '../../model/deployment.class';
import { AssetType } from 'src/app/model/asset.class';
import { AssetService } from '../../services/asset.service';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-deployment-form',
  templateUrl: './deployment-form.component.html',
  providers: [AutoSaveService]
})
export class DeploymentFormComponent implements OnInit {
  @Input() selectedNode!: Deployment;
  form!: FormGroup;
  assets$!: Observable<any[]>;
  strategyTypes = [
    { label: 'Recreate', value: 'Recreate' },
    { label: 'Rolling Update', value: 'RollingUpdate' }
  ];

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService,
    private assetService: AssetService,
    private notificationService: NotificationService
  ) {}

  ngOnInit() {
    this.initForm();
    this.setupAutoSave();
    this.loadAssets();
  }

  private initForm() {
    this.form = this.fb.group({
      name: [this.selectedNode.name, Validators.required],
      replicas: [this.selectedNode.replicas, [Validators.required, Validators.min(0)]],
      strategyType: [this.selectedNode.strategyType, Validators.required],
      assetId: [this.selectedNode.assetId || '', Validators.required],
      containerPort: [this.selectedNode.containerPort ?? 80, [Validators.required, Validators.min(1)]]
    });
  }

  private loadAssets() {
    this.assets$ = this.assetService.getAssets().pipe(
      map(assets => assets.filter(asset => asset.type === AssetType.IMAGE)),
      catchError(error => {
        console.error('Error loading assets', error);
        this.notificationService.error('Errore', 'Impossibile caricare la lista immagini');
        return of([]);
      })
    );
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }
}
