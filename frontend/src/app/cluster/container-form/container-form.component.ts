import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { FormGroup, FormBuilder, Validators, FormControl } from '@angular/forms';
import { catchError, map, Observable, of } from 'rxjs';
import { Asset } from 'src/app/model/asset.class';
import { Container, ContainerRole, toContainerRole } from 'src/app/model/container.class';
import { AssetService } from 'src/app/services/asset.service';
import { AutoSaveService } from 'src/app/services/auto-save.service';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-container-form',
  templateUrl: './container-form.component.html',
  providers: [AutoSaveService]
})
export class ContainerFormComponent implements OnInit, OnChanges {
  @Input() selectedNode!: Container;
  form!: FormGroup;
  assets$!: Observable<Asset[]>;
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

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedNode'] && !changes['selectedNode'].firstChange) {
      this.refreshFormValues(changes['selectedNode'].currentValue as Container);
    }
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

  private refreshFormValues(node: Container): void {
    if (!this.form) {
      return;
    }
    const selectedRole = toContainerRole(node?.role);
    this.form.patchValue({
      name: node.name,
      assetId: node.assetId,
      containerPort: node.containerPort,
      role: selectedRole ?? null
    }, { emitEvent: false });
  }


  private loadAssets() {
    this.assets$ = this.assetService.getImageAssets().pipe(
      catchError(error => {
        console.error('Error loading image assets:', error);
        this.notificationService.error('Error', 'Failed to load image assets');
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
