import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, FormGroup, ValidatorFn } from '@angular/forms';
import { Subscription, Observable } from 'rxjs';
import { map, skip } from 'rxjs/operators';
import * as yaml from 'js-yaml';

import { Node } from '../../model/node.class';
import {
  ConfigBundle,
  ConfigEntry,
  ConfigEntrySensitivity,
  ensureConfigBundleDefaults
} from '../../model/config-bundle.model';
import { AutoSaveService } from '../../services/auto-save.service';
import { generateManifestsFromBundle } from '../../services/config-bundle-manifest.service';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-config-bundle-form',
  templateUrl: './config-bundle-form.component.html',
  styleUrls: ['./config-bundle-form.component.scss'],
  providers: [AutoSaveService]
})
export class ConfigBundleFormComponent implements OnInit, OnChanges, OnDestroy {
  @Input() selectedNode!: Node;
  @Input() sourceNodes: Node[] = [];
  @Input() targetNodes: Node[] = [];
  @Input() clusterNamespace: string = 'default';

  form!: FormGroup;
  yamlPreview = '';
  yamlError: string | null = null;
  pasteDialogVisible = false;
  pasteBuffer = '';
  pasteDefaultSensitivity: ConfigEntrySensitivity = 'PLAIN';
  readonly sensitivityOptions = [
    { label: 'Plain', value: 'PLAIN' as ConfigEntrySensitivity },
    { label: 'Secret', value: 'SECRET' as ConfigEntrySensitivity }
  ];
  canEditName = true;

  private formSubscriptions = new Subscription();
  private entryKeySubscriptions = new Map<FormGroup, Subscription>();
  private autoSaveNodeId: string | null = null;
  private lastSelectedNodeId: string | null = null;

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.buildFormFromNode();
    this.lastSelectedNodeId = this.selectedNode?.id ?? null;
    this.setupAutoSave();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedNode'] && !changes['selectedNode'].firstChange && this.form) {
      this.refreshFormFromNode(changes['selectedNode'].currentValue as Node);
      this.lastSelectedNodeId = this.selectedNode?.id ?? null;
    }

    if ((changes['sourceNodes'] || changes['targetNodes']) && this.form) {
      this.updateNameLockState();
    }
  }

  ngOnDestroy(): void {
    this.flushPendingChanges();
    this.teardownEntryKeySubscriptions();
    this.formSubscriptions.unsubscribe();
  }

  get annotationsArray(): FormArray<FormGroup> {
    return this.form.get('annotations') as FormArray<FormGroup>;
  }

  get entriesArray(): FormArray<FormGroup> {
    return this.form.get('entries') as FormArray<FormGroup>;
  }

  get entryControls(): FormGroup[] {
    return this.entriesArray.controls as FormGroup[];
  }

  addEntry(): void {
    const group = this.createEntryGroup();
    this.entriesArray.push(group);
    this.watchEntryKey(group);
    this.runEntriesValidation();
  }

  duplicateEntry(index: number): void {
    const source = this.entriesArray.at(index) as FormGroup | null;
    if (!source) {
      return;
    }
    const clone = this.createEntryGroup({
      key: `${(source.get('key')?.value as string) || ''}-copy`,
      value: source.get('value')?.value,
      sensitivity: source.get('sensitivity')?.value
    });
    this.entriesArray.insert(index + 1, clone);
    this.watchEntryKey(clone);
    this.runEntriesValidation();
  }

  removeEntry(index: number): void {
    const group = this.entriesArray.at(index) as FormGroup | null;
    if (!group) {
      return;
    }
    this.unwatchEntryKey(group);
    this.entriesArray.removeAt(index);
    if (!this.entriesArray.length) {
      this.addEntry();
    } else {
      this.runEntriesValidation();
    }
  }

  addAnnotation(): void {
    this.annotationsArray.push(this.createAnnotationGroup());
  }

  removeAnnotation(index: number): void {
    this.annotationsArray.removeAt(index);
  }

  openPasteDialog(): void {
    this.pasteBuffer = '';
    this.pasteDefaultSensitivity = 'PLAIN';
    this.pasteDialogVisible = true;
  }

  applyPasteBuffer(): void {
    const lines = this.pasteBuffer
      .split(/\r?\n/)
      .map(line => line.trim())
      .filter(line => !!line);

    if (!lines.length) {
      this.notificationService.warn('Nothing to import', 'Provide one or more key=value lines.');
      return;
    }

    lines.forEach(line => {
      const separatorIndex = line.indexOf('=');
      if (separatorIndex <= 0) {
        return;
      }
      const key = line.substring(0, separatorIndex).trim();
      const value = line.substring(separatorIndex + 1).trim();
      if (!key) {
        return;
      }
      const entry: ConfigEntry = {
        key,
        value,
        sensitivity: this.deriveSensitivityFromKey(key) ?? this.pasteDefaultSensitivity
      };
      const group = this.createEntryGroup(entry);
      this.entriesArray.push(group);
    });

    this.runEntriesValidation();
    this.pasteDialogVisible = false;
  }

  cancelPaste(): void {
    this.pasteDialogVisible = false;
  }

  shouldMaskEntry(group: FormGroup): boolean {
    const sensitivity = group.get('sensitivity')?.value as ConfigEntrySensitivity;
    const showSecrets = this.form?.get('showSecretsAsPlain')?.value;
    return sensitivity === 'SECRET' && !showSecrets;
  }

  trackByEntryIndex(index: number): number {
    return index;
  }

  private buildFormFromNode(): void {
    const bundle = this.resolveBundleFromNode();
    this.teardownEntryKeySubscriptions();
    this.formSubscriptions.unsubscribe();
    this.formSubscriptions = new Subscription();

    this.form = this.fb.group({
      name: [bundle.name, [this.trimmedRequiredValidator(), this.dnsLabelValidator()]],
      namespace: [bundle.namespace, [this.trimmedRequiredValidator(), this.dnsLabelValidator()]],
      annotations: this.fb.array(this.buildAnnotationGroups(bundle.annotations)),
      showSecretsAsPlain: [bundle.showSecretsAsPlain ?? false],
      entries: this.fb.array(this.buildEntryGroups(bundle.entries), {
        validators: this.entriesMustHaveKeyValidator()
      })
    });

    this.watchEntryKeys();
    this.setupFormSubscriptions();
    this.updateNameLockState();
    this.runEntriesValidation();
    this.refreshYamlPreview();
  }

  private refreshFormFromNode(node: Node): void {
    const bundle = this.resolveBundleFromNode(node);
    this.teardownEntryKeySubscriptions();
    this.formSubscriptions.unsubscribe();
    this.formSubscriptions = new Subscription();

    this.form.patchValue(
      {
        name: bundle.name,
        namespace: bundle.namespace,
        showSecretsAsPlain: bundle.showSecretsAsPlain ?? false
      },
      { emitEvent: false }
    );

    this.form.setControl('annotations', this.fb.array(this.buildAnnotationGroups(bundle.annotations)));
    this.form.setControl(
      'entries',
      this.fb.array(this.buildEntryGroups(bundle.entries), {
        validators: this.entriesMustHaveKeyValidator()
      })
    );

    this.watchEntryKeys();
    this.setupFormSubscriptions();
    this.updateNameLockState();
    this.runEntriesValidation();
    this.refreshYamlPreview();
  }

  private buildEntryGroups(entries: ConfigEntry[]): FormGroup[] {
    const list = entries && entries.length ? entries : [{ key: '', value: '', sensitivity: 'PLAIN' }];
    return list.map(entry =>
      this.createEntryGroup({
        ...entry,
        sensitivity: entry?.sensitivity === 'SECRET' ? 'SECRET' : 'PLAIN'
      })
    );
  }

  private createEntryGroup(entry?: Partial<ConfigEntry>): FormGroup {
    const group = this.fb.group({
      key: [entry?.key || '', [this.trimmedRequiredValidator()]],
      value: [entry?.value ?? ''],
      sensitivity: [entry?.sensitivity === 'SECRET' ? 'SECRET' : 'PLAIN']
    });
    this.watchEntryKey(group);
    return group;
  }

  private watchEntryKeys(): void {
    this.entryControls.forEach(group => this.watchEntryKey(group));
  }

  private watchEntryKey(group: FormGroup): void {
    if (this.entryKeySubscriptions.has(group)) {
      return;
    }
    const keyControl = group.get('key');
    if (!keyControl) {
      return;
    }
    const sub = keyControl.valueChanges.subscribe(value => {
      this.applySensitivityHeuristic(group, value);
      this.runEntriesValidation();
    });
    this.entryKeySubscriptions.set(group, sub);
  }

  private unwatchEntryKey(group: FormGroup): void {
    const sub = this.entryKeySubscriptions.get(group);
    sub?.unsubscribe();
    this.entryKeySubscriptions.delete(group);
  }

  private teardownEntryKeySubscriptions(): void {
    this.entryKeySubscriptions.forEach(sub => sub.unsubscribe());
    this.entryKeySubscriptions.clear();
  }

  private setupFormSubscriptions(): void {
    this.formSubscriptions.add(
      this.entriesArray.valueChanges.subscribe(() => {
        this.runEntriesValidation();
      })
    );

    this.formSubscriptions.add(
      this.form.valueChanges.subscribe(() => {
        this.refreshYamlPreview();
      })
    );
  }

  private runEntriesValidation(): void {
    this.applyDuplicateKeyErrors();
    this.entriesArray.updateValueAndValidity({ onlySelf: true, emitEvent: false });
  }

  private resolveBundleFromNode(node: Node | null | undefined = this.selectedNode): ConfigBundle {
    if (!node) {
      return ensureConfigBundleDefaults({
        id: 'config-bundle',
        name: 'config-bundle',
        namespace: this.clusterNamespace || 'default',
        annotations: {},
        entries: [],
        showSecretsAsPlain: false
      });
    }

    const rawBundle = (node as any).configBundle as ConfigBundle | undefined;
    const fallbackEntries = (node as any).entries ?? [];
    const fallbackAnnotations = (node as any).annotations ?? {};
    const fallbackNamespace = (node as any).namespace ?? this.clusterNamespace ?? 'default';
    const effectiveEntries = rawBundle?.entries && rawBundle.entries.length ? rawBundle.entries : fallbackEntries;
    return ensureConfigBundleDefaults({
      ...(rawBundle || {}),
      id: node.id,
      name: rawBundle?.name || node.name || 'config-bundle',
      namespace: rawBundle?.namespace || fallbackNamespace,
      annotations: rawBundle?.annotations ?? fallbackAnnotations,
      entries: effectiveEntries,
      showSecretsAsPlain: rawBundle?.showSecretsAsPlain ?? (node as any).showSecretsAsPlain ?? false
    });
  }

  private createAnnotationGroup(initial?: { key?: string; value?: string }): FormGroup {
    return this.fb.group({
      key: [initial?.key || '', [this.trimmedRequiredValidator()]],
      value: [initial?.value ?? '']
    });
  }

  private buildAnnotationGroups(values?: Record<string, string>): FormGroup[] {
    if (!values || !Object.keys(values).length) {
      return [];
    }
    return Object.entries(values).map(([key, value]) => this.createAnnotationGroup({ key, value }));
  }

  private trimmedRequiredValidator(): ValidatorFn {
    return (control: AbstractControl) => {
      const value = (control.value || '').trim();
      return value ? null : { required: true };
    };
  }

  private applySensitivityHeuristic(group: FormGroup, value: string): void {
    const sensitivityControl = group.get('sensitivity');
    if (!sensitivityControl) {
      return;
    }
    const heuristic = this.deriveSensitivityFromKey(value);
    if (heuristic && sensitivityControl.pristine && sensitivityControl.value !== heuristic) {
      sensitivityControl.setValue(heuristic);
    }
  }

  private deriveSensitivityFromKey(key: string): ConfigEntrySensitivity | null {
    if (!key) {
      return null;
    }
    const normalized = key.toLowerCase();
    if (normalized.includes('password') || normalized.includes('secret')) {
      return 'SECRET';
    }
    return null;
  }

  private entriesMustHaveKeyValidator(): ValidatorFn {
    return (control: AbstractControl) => {
      if (!(control instanceof FormArray)) {
        return null;
      }
      const hasKey = control.controls.some(group => !!(group.get('key')?.value || '').trim());
      return hasKey ? null : { noKeyEntry: true };
    };
  }

  private applyDuplicateKeyErrors(): void {
    const keyMap = new Map<string, FormGroup[]>();
    this.entriesArray.controls.forEach(group => {
      const keyControl = group.get('key');
      if (!keyControl) {
        return;
      }

      // remove stale duplicateKey flag while keeping other errors
      if (keyControl.errors?.['duplicateKey']) {
        const { duplicateKey, ...rest } = keyControl.errors;
        keyControl.setErrors(Object.keys(rest).length ? rest : null);
      }

      const normalized = (keyControl.value || '').trim().toLowerCase();
      if (!normalized) {
        return;
      }
      const existing = keyMap.get(normalized) ?? [];
      existing.push(group as FormGroup);
      keyMap.set(normalized, existing);
    });

    keyMap.forEach(groups => {
      if (groups.length < 2) {
        return;
      }
      groups.forEach(group => {
        const keyControl = group.get('key');
        if (!keyControl) {
          return;
        }
        const currentErrors = keyControl.errors || {};
        keyControl.setErrors({ ...currentErrors, duplicateKey: true });
      });
    });
  }

  private dnsLabelValidator(): ValidatorFn {
    const regex = /^[a-z0-9]([-a-z0-9]*[a-z0-9])?$/;
    return (control: AbstractControl) => {
      const value = (control.value || '').trim();
      if (!value) {
        return null;
      }
      if (value.length > 253 || !regex.test(value)) {
        return { dnsLabel: true };
      }
      return null;
    };
  }

  private entriesArrayToEntries(): ConfigEntry[] {
    return this.entriesArray.controls
      .map(group => ({
        key: (group.get('key')?.value || '').trim(),
        value: group.get('value')?.value ?? '',
        sensitivity: (group.get('sensitivity')?.value === 'SECRET' ? 'SECRET' : 'PLAIN') as ConfigEntrySensitivity
      }))
      .filter(entry => !!entry.key);
  }

  private annotationsArrayToRecord(): Record<string, string> {
    const record: Record<string, string> = {};
    this.annotationsArray.controls.forEach(group => {
      const key = (group.get('key')?.value || '').trim();
      if (!key) {
        return;
      }
      record[key] = group.get('value')?.value ?? '';
    });
    return record;
  }

  private extractBundleFromForm(targetNode: Node | null = this.selectedNode): ConfigBundle {
    const raw = this.form.getRawValue();
    return ensureConfigBundleDefaults({
      id: targetNode?.id ?? 'config-bundle',
      name: (raw.name || '').trim() || targetNode?.name || 'config-bundle',
      namespace: (raw.namespace || '').trim() || (targetNode as any)?.namespace || this.clusterNamespace || 'default',
      annotations: this.annotationsArrayToRecord(),
      entries: this.entriesArrayToEntries(),
      showSecretsAsPlain: !!raw.showSecretsAsPlain
    });
  }

  private buildNodeUpdatePayload(targetNode: Node | null = this.selectedNode) {
    const bundle = this.extractBundleFromForm(targetNode);
    return {
      configBundle: bundle,
      name: bundle.name,
      namespace: bundle.namespace,
      annotations: bundle.annotations,
      entries: bundle.entries,
      showSecretsAsPlain: bundle.showSecretsAsPlain,
      hasSecrets: bundle.entries.some(entry => entry.sensitivity === 'SECRET'),
      hasPlainEntries: bundle.entries.some(entry => entry.sensitivity !== 'SECRET')
    };
  }

  private refreshYamlPreview(): void {
    if (!this.form) {
      return;
    }
    if (this.form.invalid) {
      this.yamlError = 'Resolve validation errors to preview YAML.';
      this.yamlPreview = '';
      return;
    }

    try {
      const manifests = generateManifestsFromBundle(this.extractBundleFromForm());
      if (!manifests.length) {
        this.yamlPreview = '# Bundle has no entries.';
        this.yamlError = null;
        return;
      }
      this.yamlPreview = manifests
        .map(manifest => yaml.dump(manifest, { lineWidth: -1 }).trimEnd())
        .join('\n---\n');
      this.yamlError = null;
    } catch (error: any) {
      this.yamlPreview = '';
      this.yamlError = error?.message || 'Unable to generate preview.';
    }
  }

  private updateNameLockState(): void {
    if (!this.form) {
      return;
    }
    const hasLinks = (this.sourceNodes?.length || 0) > 0 || (this.targetNodes?.length || 0) > 0;
    this.canEditName = !hasLinks;
    const nameControl = this.form.get('name');
    if (!nameControl) {
      return;
    }
    if (hasLinks) {
      nameControl.disable({ emitEvent: false });
    } else {
      nameControl.enable({ emitEvent: false });
    }
  }

  private setupAutoSave(): void {
    if (!this.form || !this.selectedNode || this.autoSaveNodeId === this.selectedNode.id) {
      return;
    }
    const payload$: Observable<any> = this.form.valueChanges.pipe(
      skip(1),
      map(() => this.buildNodeUpdatePayload(this.selectedNode))
    );
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, payload$);
    this.autoSaveNodeId = this.selectedNode.id;
  }

  private flushPendingChanges(targetNode: Node | null = this.selectedNode): void {
    if (!this.form || !targetNode?.id) {
      return;
    }
    this.autoSaveService.flushPendingChanges(targetNode.id, this.buildNodeUpdatePayload(targetNode));
  }
}
