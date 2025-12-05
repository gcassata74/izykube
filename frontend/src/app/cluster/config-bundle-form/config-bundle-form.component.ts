import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormGroup,
  ValidatorFn,
  Validators
} from '@angular/forms';
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

  private subscriptions = new Subscription();
  private entryKeySubscriptions = new Map<FormGroup, Subscription>();
  private autoSaveInitialized = false;
  private lastNodeId: string | null = null;

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.buildForm();
    this.setupAutoSave();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedNode']) {
      const prevId = this.lastNodeId;
      if (prevId && this.selectedNode && this.selectedNode.id !== prevId) {
        this.flushPendingChanges();
      }
      this.patchFormFromNode();
      this.lastNodeId = this.selectedNode?.id ?? null;
      this.setupAutoSave();
    }

    if ((changes['sourceNodes'] || changes['targetNodes']) && this.form) {
      this.updateNameLockState();
    }
  }

  ngOnDestroy(): void {
    this.flushPendingChanges();
    this.entryKeySubscriptions.forEach(sub => sub.unsubscribe());
    this.subscriptions.unsubscribe();
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

  private buildForm(): void {
    const bundle = this.resolveBundleFromNode();
    this.form = this.fb.group({
      name: [bundle.name, [Validators.required, this.dnsLabelValidator()]],
      namespace: [bundle.namespace, [Validators.required, this.dnsLabelValidator()]],
      annotations: this.fb.array(this.buildKeyValueGroups(bundle.annotations)),
      showSecretsAsPlain: [bundle.showSecretsAsPlain ?? false],
      entries: this.fb.array(
        bundle.entries.length ? bundle.entries.map(entry => this.createEntryGroup(entry)) : [this.createEntryGroup()],
        [this.atLeastOneEntryValidator()]
      )
    });

    this.setupEntryKeySubscriptions();
    this.setupDerivedSubscriptions();
    this.updateNameLockState();
    this.refreshYamlPreview();
  }

  private setupAutoSave(): void {
    if (this.autoSaveInitialized || !this.form || !this.selectedNode) {
      return;
    }
    const payload$: Observable<any> = this.form.valueChanges.pipe(
      skip(1),
      map(() => this.buildNodeUpdatePayload())
    );
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, payload$);
    this.autoSaveInitialized = true;
  }

  private patchFormFromNode(): void {
    if (!this.form) {
      return;
    }
    const bundle = this.resolveBundleFromNode();
    this.form.patchValue({
      name: bundle.name,
      namespace: bundle.namespace,
      showSecretsAsPlain: bundle.showSecretsAsPlain ?? false
    }, { emitEvent: false });

    this.resetKeyValueArray(this.annotationsArray, bundle.annotations);
    this.resetEntriesArray(bundle.entries);
    this.setupEntryKeySubscriptions();
    this.refreshYamlPreview();
    this.updateNameLockState();
  }

  private setupDerivedSubscriptions(): void {
    this.subscriptions.add(
      this.entriesArray.valueChanges.subscribe(() => {
        this.applyDuplicateKeyErrors();
        this.refreshYamlPreview();
      })
    );

    this.subscriptions.add(
      this.form.valueChanges.subscribe(() => {
        this.refreshYamlPreview();
      })
    );
  }

  private resetKeyValueArray(array: FormArray<FormGroup>, values?: Record<string, string>): void {
    array.clear({ emitEvent: false });
    const groups = this.buildKeyValueGroups(values);
    if (groups.length) {
      groups.forEach(group => array.push(group, { emitEvent: false }));
    }
  }

  private resetEntriesArray(entries: ConfigEntry[]): void {
    this.entryKeySubscriptions.forEach(sub => sub.unsubscribe());
    this.entryKeySubscriptions.clear();
    this.entriesArray.clear({ emitEvent: false });
    const groups = entries.length ? entries.map(entry => this.createEntryGroup(entry)) : [this.createEntryGroup()];
    groups.forEach(group => this.entriesArray.push(group, { emitEvent: false }));
  }

  addEntry(): void {
    const group = this.createEntryGroup();
    this.entriesArray.push(group);
    this.watchEntryKey(group);
  }

  duplicateEntry(index: number): void {
    const source = this.entriesArray.at(index) as FormGroup;
    if (!source) {
      return;
    }
    const clone = this.createEntryGroup({
      key: `${source.get('key')?.value || ''}-copy`,
      value: source.get('value')?.value,
      sensitivity: source.get('sensitivity')?.value
    });
    this.entriesArray.insert(index + 1, clone);
    this.watchEntryKey(clone);
  }

  removeEntry(index: number): void {
    const group = this.entriesArray.at(index) as FormGroup;
    if (!group) {
      return;
    }
    this.unwatchEntryKey(group);
    this.entriesArray.removeAt(index);
    if (!this.entriesArray.length) {
      this.addEntry();
    }
    this.applyDuplicateKeyErrors();
  }

  addAnnotation(): void {
    this.annotationsArray.push(this.createKeyValueGroup());
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
      this.watchEntryKey(group);
    });

    this.pasteDialogVisible = false;
    this.applyDuplicateKeyErrors();
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

  private resolveBundleFromNode(): ConfigBundle {
    if (!this.selectedNode) {
      return ensureConfigBundleDefaults({
        id: 'config-bundle',
        name: 'config-bundle',
        namespace: this.clusterNamespace || 'default',
        annotations: {},
        entries: []
      });
    }

    const nodeData = this.selectedNode as any;
    const rawBundle = nodeData.configBundle as ConfigBundle | undefined;
    const base = ensureConfigBundleDefaults({
      ...(rawBundle || {}),
      id: this.selectedNode.id,
      name: (rawBundle?.name || this.selectedNode.name),
      namespace: rawBundle?.namespace || nodeData.namespace || this.clusterNamespace || 'default',
      annotations: nodeData.annotations || rawBundle?.annotations || {},
      entries: rawBundle?.entries || []
    });

    let fallbackEntries = Array.isArray(nodeData.entries) ? nodeData.entries : base.entries;

    if ((!fallbackEntries || fallbackEntries.length === 0) && nodeData.yaml) {
      fallbackEntries = this.parseLegacyYamlEntries(
        nodeData.yaml,
        this.isSecretNode(this.selectedNode) ? 'SECRET' : 'PLAIN'
      );
    }

    return ensureConfigBundleDefaults({
      ...base,
      annotations: nodeData.annotations || base.annotations,
      entries: fallbackEntries as ConfigEntry[],
      showSecretsAsPlain: nodeData.showSecretsAsPlain ?? base.showSecretsAsPlain
    });
  }

  private createKeyValueGroup(initial?: { key?: string; value?: string }): FormGroup {
    return this.fb.group({
      key: [initial?.key || '', Validators.required],
      value: [initial?.value || '']
    });
  }

  private buildKeyValueGroups(values?: Record<string, string>): FormGroup[] {
    if (!values) {
      return [];
    }
    return Object.entries(values).map(([key, value]) => this.createKeyValueGroup({ key, value }));
  }

  private createEntryGroup(entry?: Partial<ConfigEntry>): FormGroup {
    const group = this.fb.group({
      key: [entry?.key || '', Validators.required],
      value: [entry?.value || ''],
      sensitivity: [entry?.sensitivity === 'SECRET' ? 'SECRET' : 'PLAIN']
    });
    this.watchEntryKey(group);
    return group;
  }

  private setupEntryKeySubscriptions(): void {
    this.entryKeySubscriptions.forEach(sub => sub.unsubscribe());
    this.entryKeySubscriptions.clear();
    this.entryControls.forEach(group => this.watchEntryKey(group));
  }

  private watchEntryKey(group: FormGroup): void {
    if (this.entryKeySubscriptions.has(group)) {
      return;
    }
    const sub = group.get('key')?.valueChanges.subscribe(value => {
      this.applySensitivityHeuristic(group, value);
      this.applyDuplicateKeyErrors();
    });
    if (sub) {
      this.entryKeySubscriptions.set(group, sub);
    }
  }

  private unwatchEntryKey(group: FormGroup): void {
    const sub = this.entryKeySubscriptions.get(group);
    sub?.unsubscribe();
    this.entryKeySubscriptions.delete(group);
  }

  private applySensitivityHeuristic(group: FormGroup, value: string): void {
    const sensitivityControl = group.get('sensitivity');
    if (!sensitivityControl || !value) {
      return;
    }
    const heuristic = this.deriveSensitivityFromKey(value);
    if (heuristic && sensitivityControl.value !== heuristic) {
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

  private parseLegacyYamlEntries(yamlContent: string, defaultSensitivity: ConfigEntrySensitivity): ConfigEntry[] {
    if (!yamlContent) {
      return [];
    }
    try {
      const parsed = yaml.load(yamlContent);
      if (!parsed || typeof parsed !== 'object') {
        return [];
      }
      const root = parsed as Record<string, any>;
      let source: Record<string, any> | undefined;
      if (root['data'] && typeof root['data'] === 'object') {
        source = root['data'] as Record<string, any>;
        defaultSensitivity = 'PLAIN';
      } else if (root['stringData'] && typeof root['stringData'] === 'object') {
        source = root['stringData'] as Record<string, any>;
        defaultSensitivity = 'SECRET';
      } else {
        source = root;
      }
      if (!source) {
        return [];
      }
      return Object.entries(source).map(([key, value]) => ({
        key,
        value: value == null ? '' : String(value),
        sensitivity: defaultSensitivity
      }));
    } catch {
      return [];
    }
  }

  private isSecretNode(node: Node | undefined | null): boolean {
    const kind = (node as any)?.kind?.toLowerCase?.() ?? '';
    return kind === 'secret' || !!(node as any)?.secret;
  }

  private buildNodeUpdatePayload() {
    const bundle = this.extractBundleFromForm();
    return {
      name: bundle.name,
      namespace: bundle.namespace,
      annotations: bundle.annotations,
      entries: bundle.entries,
      showSecretsAsPlain: bundle.showSecretsAsPlain,
      hasSecrets: bundle.entries.some(entry => entry.sensitivity === 'SECRET'),
      hasPlainEntries: bundle.entries.some(entry => entry.sensitivity !== 'SECRET')
    };
  }

  private extractBundleFromForm(): ConfigBundle {
    const raw = this.form.getRawValue();
    return ensureConfigBundleDefaults({
      id: this.selectedNode?.id,
      name: raw.name?.trim() || this.selectedNode?.name,
      namespace: raw.namespace?.trim() || this.clusterNamespace || 'default',
      annotations: this.keyValueArrayToRecord(this.annotationsArray),
      entries: this.entriesArrayToEntries(),
      showSecretsAsPlain: !!raw.showSecretsAsPlain
    });
  }

  private keyValueArrayToRecord(array: FormArray<FormGroup>): Record<string, string> {
    const record: Record<string, string> = {};
    array.controls.forEach(group => {
      const key = (group.get('key')?.value || '').trim();
      if (!key) {
        return;
      }
      record[key] = group.get('value')?.value ?? '';
    });
    return record;
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

  private atLeastOneEntryValidator(): ValidatorFn {
    return (control: AbstractControl) => {
      if (!(control instanceof FormArray)) {
        return null;
      }
      const hasKey = control.controls.some(group => {
        const key = (group.get('key')?.value || '').trim();
        return !!key;
      });
      return hasKey ? null : { missingEntry: true };
    };
  }

  private applyDuplicateKeyErrors(): void {
    if (!this.entriesArray?.length) {
      return;
    }
    const keyMap = new Map<string, FormGroup[]>();
    this.entriesArray.controls.forEach(group => {
      const keyControl = group.get('key');
      if (!keyControl) {
        return;
      }
      this.clearDuplicateKeyError(keyControl);
      const normalized = (keyControl.value || '').trim().toLowerCase();
      if (!normalized) {
        return;
      }
      const list = keyMap.get(normalized) || [];
      list.push(group);
      keyMap.set(normalized, list);
    });

    keyMap.forEach(groups => {
      if (groups.length <= 1) {
        return;
      }
      groups.forEach(group => {
        const keyControl = group.get('key');
        if (!keyControl) {
          return;
        }
        const errors = { ...(keyControl.errors || {}) };
        errors['duplicateKey'] = true;
        keyControl.setErrors(errors);
      });
    });
  }

  private clearDuplicateKeyError(control: AbstractControl): void {
    if (!control.errors || !control.errors['duplicateKey']) {
      return;
    }
    const { duplicateKey, ...rest } = control.errors;
    if (Object.keys(rest).length) {
      control.setErrors(rest);
    } else {
      control.setErrors(null);
    }
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

  private flushPendingChanges(): void {
    if (!this.form || !this.selectedNode) {
      return;
    }
    const payload = this.buildNodeUpdatePayload();
    this.autoSaveService.flushPendingChanges(this.selectedNode.id, payload);
  }
}
