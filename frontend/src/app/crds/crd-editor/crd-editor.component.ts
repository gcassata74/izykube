import { Component, OnDestroy, OnInit } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, map, of, Subscription, tap } from 'rxjs';
import { CrdDefinition, CrdFieldType, CrdSchemaField, CrdScope } from 'src/app/model/crd-definition.class';
import { CrdService } from 'src/app/services/crd.service';
import { NotificationService } from 'src/app/services/notification.service';
import { deriveKind, deriveMetadataName, derivePlural, generateCrdYaml } from '../crd-generator';
import { allowedFieldName, applyDuplicateFieldNameErrors, trimmedRequired } from '../crd-validators';

@Component({
  selector: 'app-crd-editor',
  templateUrl: './crd-editor.component.html',
  styleUrls: ['./crd-editor.component.scss']
})
export class CrdEditorComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  id: string | null = null;
  isNew = true;
  showYamlPreview = false;
  yamlPreview = '';
  yamlError: string | null = null;

  readonly scopeOptions = [
    { label: $localize`:@@crd.scope.namespaced:Namespaced`, value: 'Namespaced' as CrdScope },
    { label: $localize`:@@crd.scope.cluster:Cluster`, value: 'Cluster' as CrdScope },
  ];

  readonly fieldTypeOptions = [
    { label: $localize`:@@crd.fieldType.string:string`, value: 'string' as CrdFieldType },
    { label: $localize`:@@crd.fieldType.number:number`, value: 'number' as CrdFieldType },
    { label: $localize`:@@crd.fieldType.boolean:boolean`, value: 'boolean' as CrdFieldType },
    { label: $localize`:@@crd.fieldType.object:object`, value: 'object' as CrdFieldType },
    { label: $localize`:@@crd.fieldType.array:array`, value: 'array' as CrdFieldType },
  ];

  private subscriptions = new Subscription();

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private crdService: CrdService,
    private notificationService: NotificationService,
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      group: ['', [trimmedRequired()]],
      singularName: ['', [trimmedRequired()]],
      scope: ['Namespaced', [Validators.required]],
      version: ['v1', [trimmedRequired()]],
      schemaFields: this.fb.array([]),
    });

    if (!this.schemaFieldsArray.length) {
      this.addSchemaField();
    }

    this.subscriptions.add(
      this.route.paramMap.pipe(
        map(pm => pm.get('id')),
        tap(id => {
          this.id = id;
          this.isNew = !id;
          if (id) {
            this.load(id);
          } else {
            this.refreshYamlPreview();
          }
        })
      ).subscribe()
    );

    this.subscriptions.add(
      this.form.valueChanges.subscribe(() => {
        this.runSchemaValidation();
        this.refreshYamlPreview();
      })
    );
  }

  get schemaFieldsArray(): FormArray<FormGroup> {
    return this.form.get('schemaFields') as FormArray<FormGroup>;
  }

  get schemaFieldGroups(): FormGroup[] {
    return this.schemaFieldsArray.controls as FormGroup[];
  }

  addSchemaField(initial?: Partial<CrdSchemaField>): void {
    const group = this.fb.group({
      fieldName: [initial?.fieldName || '', [trimmedRequired(), allowedFieldName()]],
      fieldType: [initial?.fieldType || 'string', [Validators.required]],
    });
    this.schemaFieldsArray.push(group);
    this.runSchemaValidation();
  }

  removeSchemaField(index: number): void {
    if (this.schemaFieldsArray.length <= 1) {
      return;
    }
    this.schemaFieldsArray.removeAt(index);
    this.runSchemaValidation();
  }

  private runSchemaValidation(): void {
    applyDuplicateFieldNameErrors(this.schemaFieldsArray);
    this.schemaFieldsArray.updateValueAndValidity({ onlySelf: true, emitEvent: false });
  }

  get pluralPreview(): string {
    return derivePlural(this.form.get('singularName')?.value || '');
  }

  get kindPreview(): string {
    return deriveKind(this.form.get('singularName')?.value || '');
  }

  get metadataNamePreview(): string {
    return deriveMetadataName(this.pluralPreview, this.form.get('group')?.value || '');
  }

  toggleYamlPreview(): void {
    this.showYamlPreview = !this.showYamlPreview;
  }

  get yamlPreviewToggleLabel(): string {
    return this.showYamlPreview
      ? $localize`:@@crd.hideYamlPreview:Hide YAML preview`
      : $localize`:@@crd.showYamlPreview:Show YAML preview`;
  }

  private refreshYamlPreview(): void {
    if (!this.showYamlPreview) {
      return;
    }
    if (this.form.invalid) {
      this.yamlPreview = '';
      this.yamlError = $localize`:@@crd.yaml.resolveValidation:Resolve validation errors to preview YAML.`;
      return;
    }
    try {
      this.yamlPreview = generateCrdYaml(this.toPayload());
      this.yamlError = null;
    } catch (e: any) {
      this.yamlPreview = '';
      this.yamlError = e?.message || $localize`:@@crd.yaml.generateFailed:Unable to generate preview.`;
    }
  }

  private load(id: string): void {
    this.subscriptions.add(
      this.crdService.get(id).pipe(
        tap(crd => this.applyCrd(crd)),
        catchError(err => {
          console.error('Error loading CRD:', err);
          this.notificationService.error(
            $localize`:@@common.error:Error`,
            $localize`:@@crd.error.load:Failed to load CRD`
          );
          this.router.navigate(['/crds']);
          return of(null);
        })
      ).subscribe()
    );
  }

  private applyCrd(crd: CrdDefinition): void {
    this.form.patchValue({
      group: crd.group,
      singularName: crd.singularName,
      scope: crd.scope,
      version: crd.version,
    }, { emitEvent: false });

    const fields = (crd.schemaFields && crd.schemaFields.length) ? crd.schemaFields : [{ fieldName: '', fieldType: 'string' as CrdFieldType }];
    const array = this.fb.array(fields.map(field => this.fb.group({
      fieldName: [field.fieldName || '', [trimmedRequired(), allowedFieldName()]],
      fieldType: [field.fieldType || 'string', [Validators.required]],
    })));
    this.form.setControl('schemaFields', array);

    this.runSchemaValidation();
    this.form.updateValueAndValidity({ emitEvent: false });
    this.refreshYamlPreview();
  }

  save(): void {
    this.runSchemaValidation();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notificationService.warn(
        $localize`:@@common.invalid:Invalid`,
        $localize`:@@crd.error.fixBeforeSave:Fix form errors before saving.`
      );
      return;
    }

    const payload = this.toPayload();
    const request$ = this.isNew
      ? this.crdService.create(payload)
      : this.crdService.update(this.id!, payload);

    this.subscriptions.add(
      request$.pipe(
        tap(saved => {
          this.notificationService.success(
            $localize`:@@common.saved:Saved`,
            $localize`:@@crd.saved:CRD saved successfully`
          );
          this.router.navigate(['/crds']);
        }),
        catchError(err => {
          console.error('Error saving CRD:', err);
          this.notificationService.error(
            $localize`:@@common.saveFailed:Save failed`,
            err?.error || $localize`:@@crd.error.save:Failed to save CRD`
          );
          return of(null);
        })
      ).subscribe()
    );
  }

  cancel(): void {
    this.router.navigate(['/crds']);
  }

  private toPayload(): Pick<CrdDefinition, 'group' | 'singularName' | 'scope' | 'version' | 'schemaFields'> {
    const raw = this.form.getRawValue();
    return {
      group: (raw.group || '').trim(),
      singularName: (raw.singularName || '').trim(),
      scope: raw.scope,
      version: (raw.version || '').trim() || 'v1',
      schemaFields: (raw.schemaFields || []).map((f: any) => ({
        fieldName: (f.fieldName || '').trim(),
        fieldType: f.fieldType,
      })),
    };
  }

  fieldError(group: FormGroup, name: 'fieldName' | 'fieldType'): any {
    const control = group.get(name);
    if (!control || !(control.dirty || control.touched)) {
      return null;
    }
    return control.errors || null;
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }
}
