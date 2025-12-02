import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
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
export class DeploymentFormComponent implements OnInit, OnChanges {
  @Input() selectedNode!: Deployment;
  form!: FormGroup;
  assets$!: Observable<any[]>;
  strategyTypes = [
    { label: 'Recreate', value: 'Recreate' },
    { label: 'Rolling Update', value: 'RollingUpdate' }
  ];
  workloadOptions = [
    { label: 'Deployment', value: 'DEPLOYMENT' },
    { label: 'StatefulSet', value: 'STATEFULSET' },
    { label: 'DaemonSet', value: 'DAEMONSET' }
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

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedNode'] && !changes['selectedNode'].firstChange) {
      this.refreshFormValues(changes['selectedNode'].currentValue as Deployment);
    }
  }

  private initForm() {
    this.form = this.fb.group({
      name: [this.selectedNode.name, Validators.required],
      replicas: [this.selectedNode.replicas, [Validators.required, Validators.min(0)]],
      strategyType: [this.selectedNode.strategyType, Validators.required],
      assetId: [this.selectedNode.assetId || '', Validators.required],
      containerPort: [this.selectedNode.containerPort ?? 80, [Validators.required, Validators.min(1)]],
      workloadType: [this.selectedNode.workloadType ?? 'DEPLOYMENT', Validators.required]
    });
  }

  private refreshFormValues(node: Deployment): void {
    if (!this.form) {
      return;
    }
    this.form.patchValue({
      name: node.name,
      replicas: node.replicas,
      strategyType: node.strategyType,
      assetId: node.assetId || '',
      containerPort: node.containerPort ?? 80,
      workloadType: node.workloadType ?? 'DEPLOYMENT'
    }, { emitEvent: false });
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
