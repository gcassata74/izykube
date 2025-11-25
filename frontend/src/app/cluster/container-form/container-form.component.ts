import { Component, Input } from '@angular/core';
import { FormGroup, FormBuilder, Validators, FormControl } from '@angular/forms';
import { catchError, map, Observable, of, tap } from 'rxjs';
import { AssetType } from 'src/app/model/asset.class';
import { Container, ContainerRole, toContainerRole } from 'src/app/model/container.class';
import { AssetService } from 'src/app/services/asset.service';
import { AutoSaveService } from 'src/app/services/auto-save.service';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-container-form',
  templateUrl: './container-form.component.html',
  providers: [AutoSaveService]
})
export class ContainerFormComponent {
  @Input() selectedNode!: Container;
  form!: FormGroup;
  assets$!: Observable<any[]>;
  readonly roleOptions = [
    { label: 'Init container', value: 'INIT' as ContainerRole },
    { label: 'Sidecar', value: 'SIDECAR' as ContainerRole }
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
    const selectedRole = toContainerRole(this.selectedNode?.role);
    this.form = this.fb.group({
      name: [this.selectedNode.name, Validators.required],
      assetId: [this.selectedNode.assetId, Validators.required],
      containerPort: [this.selectedNode.containerPort, [Validators.required, Validators.min(1)]],
      role: new FormControl<ContainerRole | null>(selectedRole ?? null)
    });
  }


  private loadAssets() {
    this.assets$ = this.assetService.getAssets().pipe(
        map(assets => assets.filter(asset => asset.type === AssetType.IMAGE)),
        tap(playbooks => console.log('Loaded playbooks:', playbooks)),
        catchError(error => {
            console.error('Error loading playbooks:', error);
            this.notificationService.error('Error', 'Failed to load playbooks');
            return of([]);
        })
    );
  }

  private setupAutoSave() {
    const normalizedChanges = this.form.valueChanges.pipe(
      map(value => {
        const normalized = { ...value } as any;
        if (!normalized.role) {
          delete normalized.role;
        }
        return normalized;
      })
    );
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, normalizedChanges);
  }
}
