import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { catchError, map, Observable, of, Subscription } from 'rxjs';
import { AutoSaveService } from '../../services/auto-save.service';
import { Deployment } from '../../model/deployment.class';
import { AssetType } from 'src/app/model/asset.class';
import { AssetService } from '../../services/asset.service';
import { NotificationService } from '../../services/notification.service';
import { KubeExplorerService } from '../../services/kube-explorer.service';
import { distinctUntilChanged, filter } from 'rxjs/operators';

@Component({
  selector: 'app-deployment-form',
  templateUrl: './deployment-form.component.html',
  providers: [AutoSaveService]
})
export class DeploymentFormComponent implements OnInit, OnChanges, OnDestroy {
  @Input() selectedNode!: Deployment;
  @Input() clusterNamespace?: string;
  form!: FormGroup;
  assets$!: Observable<any[]>;
  private subscription = new Subscription();
  private meshToggleReady = false;
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
    private notificationService: NotificationService,
    private kubeExplorerService: KubeExplorerService
  ) {}

  ngOnInit() {
    this.initForm();
    this.setupAutoSave();
    this.loadAssets();
    this.setupMeshToggleListener();
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
      workloadType: [this.selectedNode.workloadType ?? 'DEPLOYMENT', Validators.required],
      addToMesh: [this.selectedNode.addToMesh ?? false],
      command: [this.stringifyRuntimeList(this.selectedNode.command)],
      args: [this.stringifyRuntimeList(this.selectedNode.args)]
    });
    this.meshToggleReady = false;
    queueMicrotask(() => {
      this.meshToggleReady = true;
    });
  }

  private refreshFormValues(node: Deployment): void {
    if (!this.form) {
      return;
    }
    this.meshToggleReady = false;
    this.form.patchValue({
      name: node.name,
      replicas: node.replicas,
      strategyType: node.strategyType,
      assetId: node.assetId || '',
      containerPort: node.containerPort ?? 80,
      workloadType: node.workloadType ?? 'DEPLOYMENT',
      addToMesh: node.addToMesh ?? false,
      command: this.stringifyRuntimeList(node.command),
      args: this.stringifyRuntimeList(node.args)
    }, { emitEvent: false });
    queueMicrotask(() => {
      this.meshToggleReady = true;
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
    const runtimeAwareChanges$ = this.form.valueChanges.pipe(
      map((value) => ({
        ...value,
        command: this.parseRuntimeList(value?.command),
        args: this.parseRuntimeList(value?.args)
      }))
    );
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, runtimeAwareChanges$);
    const workloadTypeControl = this.form.get('workloadType');
    if (workloadTypeControl) {
      this.subscription.add(
        workloadTypeControl.valueChanges.pipe(
          map(value => String(value ?? '').toUpperCase()),
          filter(value => value === 'DEPLOYMENT' || value === 'STATEFULSET' || value === 'DAEMONSET'),
          distinctUntilChanged()
        ).subscribe(workloadType => {
          // Persist workload type even when other required fields (e.g. assetId) are temporarily invalid.
          this.autoSaveService.flushPendingChanges(this.selectedNode.id, { workloadType });
        })
      );
    }
  }

  private stringifyRuntimeList(values?: string[] | null): string {
    if (!values || values.length === 0) {
      return '';
    }
    return values.join('\n');
  }

  private parseRuntimeList(raw: unknown): string[] {
    if (typeof raw !== 'string') {
      return [];
    }
    return raw
      .split('\n')
      .map((entry) => entry.trim())
      .filter((entry) => !!entry);
  }

  private setupMeshToggleListener(): void {
    const control = this.form.get('addToMesh');
    if (!control) {
      return;
    }
    this.subscription.add(
      control.valueChanges.pipe(
        distinctUntilChanged(),
        filter(() => this.meshToggleReady)
      ).subscribe((enabled) => {
        const namespace = this.clusterNamespace || 'default';
        const name = this.form.get('name')?.value;
        if (!name) {
          this.notificationService.warn(
            $localize`:@@deploymentForm.warn.nameRequiredTitle:Deployment name required`,
            $localize`:@@deploymentForm.warn.nameRequiredDetail:Set a deployment name before updating mesh.`
          );
          return;
        }
        this.kubeExplorerService.setDeploymentMesh(namespace, name, !!enabled).subscribe({
          next: () => {
            this.notificationService.success('Mesh updated', `${name} ${enabled ? 'added to' : 'removed from'} mesh.`);
          },
          error: (error) => {
            const detail = error?.error || error?.message || 'Unable to update mesh settings.';
            this.notificationService.error('Mesh update failed', typeof detail === 'string' ? detail : undefined);
          }
        });
      })
    );
  }

  ngOnDestroy(): void {
    const selectedNodeId = this.selectedNode?.id;
    const workloadType = String(this.form?.get('workloadType')?.value ?? this.selectedNode?.workloadType ?? 'DEPLOYMENT').toUpperCase();
    if (workloadType === 'DEPLOYMENT' || workloadType === 'STATEFULSET' || workloadType === 'DAEMONSET') {
      if (selectedNodeId) {
        this.autoSaveService.flushPendingChanges(selectedNodeId, { workloadType });
      }
    }
    this.subscription.unsubscribe();
  }
}
