import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { AbstractControl, FormBuilder, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { catchError, map, skip, tap } from 'rxjs/operators';
import { CustomResource } from 'src/app/model/custom-resource.class';
import { CrdDefinition, CrdDefinitionSummary, CrdSchemaField } from 'src/app/model/crd-definition.class';
import { AutoSaveService } from 'src/app/services/auto-save.service';
import { CrdService } from 'src/app/services/crd.service';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-custom-resource-form',
  templateUrl: './custom-resource-form.component.html',
  styleUrls: ['./custom-resource-form.component.scss'],
  providers: [AutoSaveService]
})
export class CustomResourceFormComponent implements OnInit, OnChanges, OnDestroy {
  @Input() selectedNode!: CustomResource;
  @Input() clusterNamespace = 'default';

  form!: FormGroup;
  crdOptions: { label: string; value: string }[] = [];
  selectedCrd: CrdDefinition | null = null;
  schemaFields: CrdSchemaField[] = [];
  loadingCrds = false;
  loadingSchema = false;
  private autoSaveNodeId: string | null = null;

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService,
    private crdService: CrdService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.loadCrdOptions();
    this.setupAutoSave();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedNode'] && !changes['selectedNode'].firstChange && this.form) {
      this.refreshForm(changes['selectedNode'].currentValue as CustomResource);
      this.loadSchemaIfNeeded();
      this.setupAutoSave();
    }
  }

  ngOnDestroy(): void {
    if (this.selectedNode?.id && this.form) {
      this.autoSaveService.flushPendingChanges(this.selectedNode.id, this.buildPayload());
    }
  }

  get specGroup(): FormGroup {
    return this.form.get('spec') as FormGroup;
  }

  getSpecControl(fieldName: string): FormControl {
    return this.specGroup.get(fieldName) as FormControl;
  }

  trackByField(_: number, field: CrdSchemaField): string {
    return field.fieldName;
  }

  isJsonField(field: CrdSchemaField): boolean {
    return field.fieldType === 'object' || field.fieldType === 'array';
  }

  isNumericField(field: CrdSchemaField): boolean {
    return field.fieldType === 'number';
  }

  isBooleanField(field: CrdSchemaField): boolean {
    return field.fieldType === 'boolean';
  }

  private initForm(): void {
    const node = this.selectedNode;
    this.form = this.fb.group({
      name: [node?.name ?? '', Validators.required],
      crdId: [node?.crdId ?? null, Validators.required],
      namespace: [(node as any)?.namespace ?? (this.clusterNamespace || 'default')],
      spec: this.fb.group({})
    });

    this.form.get('crdId')?.valueChanges.subscribe((crdId: string | null) => {
      if (!crdId) {
        this.selectedCrd = null;
        this.schemaFields = [];
        this.form.setControl('spec', this.fb.group({}));
        return;
      }
      this.loadCrdSchema(crdId, {});
    });
  }

  private refreshForm(node: CustomResource): void {
    this.form.patchValue({
      name: node?.name ?? '',
      crdId: node?.crdId ?? null,
      namespace: (node as any)?.namespace ?? (this.clusterNamespace || 'default')
    }, { emitEvent: false });
  }

  private loadCrdOptions(): void {
    this.loadingCrds = true;
    this.crdService.listAvailable().pipe(
      map((list: CrdDefinitionSummary[]) => list || []),
      tap((list: CrdDefinitionSummary[]) => {
        this.crdOptions = list.map(item => ({
          label: `${item.kind || item.singularName} (${item.group}/${item.version})`,
          value: item.id
        }));
        const currentNode = this.selectedNode as any;
        if (!this.crdOptions.length && currentNode?.crdId && currentNode?.crdKind && currentNode?.crdGroup && currentNode?.crdVersion) {
          this.crdOptions = [{
            label: `${currentNode.crdKind} (${currentNode.crdGroup}/${currentNode.crdVersion})`,
            value: currentNode.crdId
          }];
        }
      }),
      tap(() => this.loadSchemaIfNeeded()),
      catchError(() => {
        this.crdOptions = [];
        this.notificationService.error('CRD load failed', 'Unable to load CRD list.');
        return of([]);
      })
    ).subscribe(() => {
      this.loadingCrds = false;
    });
  }

  private loadSchemaIfNeeded(): void {
    const crdId = this.form?.get('crdId')?.value as string | null;
    if (!crdId) {
      return;
    }
    const specValues = (this.selectedNode as any)?.spec || {};
    this.loadCrdSchema(crdId, specValues);
  }

  private loadCrdSchema(crdId: string, existingSpec: Record<string, any>): void {
    this.loadingSchema = true;
    this.crdService.getAvailable(crdId).pipe(
      tap((definition: CrdDefinition) => {
        this.selectedCrd = definition;
        this.schemaFields = definition?.schemaFields || [];
        this.form.setControl('spec', this.createSpecGroup(this.schemaFields, existingSpec));
      }),
      catchError(() => {
        this.selectedCrd = null;
        this.schemaFields = [];
        this.form.setControl('spec', this.fb.group({}));
        this.notificationService.error('CRD load failed', 'Unable to load selected CRD schema.');
        return of(null);
      })
    ).subscribe(() => {
      this.loadingSchema = false;
    });
  }

  private createSpecGroup(fields: CrdSchemaField[], existingSpec: Record<string, any>): FormGroup {
    const group = this.fb.group({});
    (fields || []).forEach(field => {
      const initialValue = this.toFormValue(field, existingSpec?.[field.fieldName]);
      const validators = this.isJsonField(field) ? [this.jsonValidator(field.fieldType as 'object' | 'array')] : [];
      group.addControl(field.fieldName, this.fb.control(initialValue, validators));
    });
    return group;
  }

  private toFormValue(field: CrdSchemaField, raw: any): any {
    if (raw === undefined || raw === null) {
      if (field.fieldType === 'boolean') {
        return false;
      }
      return '';
    }
    if (field.fieldType === 'object' || field.fieldType === 'array') {
      try {
        return JSON.stringify(raw, null, 2);
      } catch {
        return '';
      }
    }
    return raw;
  }

  private jsonValidator(expectedType: 'object' | 'array'): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (value === null || value === undefined || String(value).trim() === '') {
        return null;
      }
      try {
        const parsed = JSON.parse(String(value));
        if (expectedType === 'array' && !Array.isArray(parsed)) {
          return { invalidJsonType: true };
        }
        if (expectedType === 'object' && (Array.isArray(parsed) || typeof parsed !== 'object')) {
          return { invalidJsonType: true };
        }
        return null;
      } catch {
        return { invalidJson: true };
      }
    };
  }

  private buildSpecPayload(): Record<string, any> {
    const payload: Record<string, any> = {};
    (this.schemaFields || []).forEach(field => {
      const control = this.getSpecControl(field.fieldName);
      if (!control) {
        return;
      }
      const raw = control.value;
      if (raw === '' || raw === null || raw === undefined) {
        return;
      }
      switch (field.fieldType) {
        case 'number': {
          const parsed = Number(raw);
          if (!Number.isNaN(parsed)) {
            payload[field.fieldName] = parsed;
          }
          break;
        }
        case 'boolean':
          payload[field.fieldName] = !!raw;
          break;
        case 'object':
        case 'array':
          try {
            payload[field.fieldName] = JSON.parse(String(raw));
          } catch {
            payload[field.fieldName] = field.fieldType === 'array' ? [] : {};
          }
          break;
        default:
          payload[field.fieldName] = String(raw);
          break;
      }
    });
    return payload;
  }

  private buildPayload(): any {
    const namespace = String(this.form.get('namespace')?.value || this.clusterNamespace || 'default').trim() || 'default';
    return {
      name: String(this.form.get('name')?.value || '').trim(),
      kind: 'cr',
      namespace,
      crdId: this.selectedCrd?.id || this.form.get('crdId')?.value || null,
      crdGroup: this.selectedCrd?.group || '',
      crdVersion: this.selectedCrd?.version || '',
      crdKind: this.selectedCrd?.kind || '',
      crdPlural: this.selectedCrd?.plural || '',
      crdScope: this.selectedCrd?.scope || 'Namespaced',
      spec: this.buildSpecPayload()
    };
  }

  private setupAutoSave(): void {
    if (!this.form || !this.selectedNode || this.autoSaveNodeId === this.selectedNode.id) {
      return;
    }
    const payload$: Observable<any> = this.form.valueChanges.pipe(
      skip(1),
      map(() => this.buildPayload())
    );
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, payload$);
    this.autoSaveNodeId = this.selectedNode.id;
  }
}
