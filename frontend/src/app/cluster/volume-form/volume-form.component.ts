/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import { Component, Input, OnInit, OnDestroy, OnChanges, SimpleChanges } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Volume, VolumeConfig, VolumeItem, VolumeType } from '../../model/volume.class';
import { AutoSaveService } from '../../services/auto-save.service';
import { Subscription, tap } from 'rxjs';
import { PersistentVolumeService } from '../../services/persistent-volume.service';
import { PersistentVolume } from '../../model/persistent-volume.class';

@Component({
  selector: 'app-volume-form',
  templateUrl: './volume-form.component.html',
  styleUrls: ['./volume-form.component.scss'],
  providers: [AutoSaveService]
})
export class VolumeFormComponent implements OnInit, OnChanges, OnDestroy {
  @Input() selectedNode!: Volume;
  form!: FormGroup;
  private autoSaveSubscription: Subscription = new Subscription();
  persistentVolumeOptions: { label: string; value: string }[] = [];
  loadingPersistentVolumes = false;

  volumeTypes: { label: string; value: VolumeType }[] = [
    { label: $localize`:@@volumeForm.type.emptyDir:Empty Dir`, value: 'emptyDir' },
    { label: $localize`:@@volumeForm.type.hostPath:Host Path`, value: 'hostPath' },
    { label: $localize`:@@volumeForm.type.persistentVolumeClaim:Persistent Volume Claim`, value: 'persistentVolumeClaim' },
    { label: $localize`:@@volumeForm.type.configMap:Config Map`, value: 'configMap' },
    { label: $localize`:@@common.secret:Secret`, value: 'secret' }
  ];
  readonly itemSupportedTypes: VolumeType[] = ['configMap', 'secret'];

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService,
    private persistentVolumeService: PersistentVolumeService
  ) {}

  ngOnInit() {
    this.initForm();
    this.setupAutoSave();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['selectedNode'] && !changes['selectedNode'].firstChange) {
      this.initForm();
      this.setupAutoSave();
    }
  }

  ngOnDestroy() {
    this.autoSaveSubscription.unsubscribe();
  }

  private initForm() {
    this.form = this.fb.group({
      name: [this.selectedNode.name, Validators.required],
      config: this.fb.group({
        type: [this.selectedNode.config.type, Validators.required],
        mountPath: [this.selectedNode.config.mountPath, Validators.required]
      })
    });

    this.updateFormForVolumeType(this.selectedNode.config.type);

    this.form.get('config.type')?.valueChanges.subscribe((type: VolumeType) => {
      this.updateFormForVolumeType(type);
      if (type === 'persistentVolumeClaim') {
        this.loadPersistentVolumes();
        this.watchClaimName();
      }
    });

    if (this.selectedNode.config.type === 'persistentVolumeClaim') {
      this.loadPersistentVolumes();
      this.watchClaimName();
    }
  }

  private updateFormForVolumeType(type: VolumeType) {
    const configForm = this.form.get('config') as FormGroup;
    const currentConfig = this.selectedNode.config;

    // Remove all type-specific controls
    ['medium', 'sizeLimit', 'path', 'hostPathType', 'claimName', 'readOnly', 'name', 'secretName', 'optional', 'items'].forEach(control => {
      if (configForm.get(control)) {
        configForm.removeControl(control);
      }
    });

    // Add new controls based on the selected type
    switch (type) {
      case 'emptyDir':
        configForm.addControl('medium', this.fb.control(currentConfig.type === 'emptyDir' ? currentConfig.medium : ''));
        configForm.addControl('sizeLimit', this.fb.control(currentConfig.type === 'emptyDir' ? currentConfig.sizeLimit : ''));
        break;
      case 'hostPath':
        configForm.addControl('path', this.fb.control(currentConfig.type === 'hostPath' ? currentConfig.path : '', Validators.required));
        configForm.addControl('hostPathType', this.fb.control(currentConfig.type === 'hostPath' ? currentConfig.hostPathType : ''));
        break;
      case 'persistentVolumeClaim':
        const initialClaim = currentConfig.type === 'persistentVolumeClaim' ? currentConfig.claimName : '';
        if (initialClaim) {
          this.ensureClaimOption(initialClaim);
        }
        configForm.addControl('claimName', this.fb.control(initialClaim, Validators.required));
        configForm.addControl('readOnly', this.fb.control(currentConfig.type === 'persistentVolumeClaim' ? currentConfig.readOnly : false));
        break;
      case 'configMap': {
        const configMapConfig = currentConfig.type === 'configMap' ? currentConfig : undefined;
        configForm.addControl('name', this.fb.control(configMapConfig?.name || '', Validators.required));
        configForm.addControl('optional', this.fb.control(configMapConfig?.optional ?? false));
        configForm.addControl('items', this.buildItemsArray(configMapConfig?.items));
        break;
      }
      case 'secret':
        const secretConfig = currentConfig.type === 'secret' ? currentConfig : undefined;
        configForm.addControl('secretName', this.fb.control(secretConfig?.secretName || '', Validators.required));
        configForm.addControl('optional', this.fb.control(secretConfig?.optional ?? false));
        configForm.addControl('items', this.buildItemsArray(secretConfig?.items));
        break;
    }
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges.pipe(
      tap((changes) => console.log('Form value changed',changes))
    )
    );
  }

  get itemsArray(): FormArray<FormGroup> | null {
    if (!this.form) {
      return null;
    }
    const itemsControl = this.configGroup.get('items');
    return itemsControl instanceof FormArray ? itemsControl as FormArray<FormGroup> : null;
  }

  get configGroup(): FormGroup {
    return this.form.get('config') as FormGroup;
  }

  get itemControls(): FormGroup[] {
    return this.itemsArray ? (this.itemsArray.controls as FormGroup[]) : [];
  }

  supportsItemEditor(): boolean {
    if (!this.form) {
      return false;
    }
    const type = this.configGroup.get('type')?.value as VolumeType;
    return this.itemSupportedTypes.includes(type);
  }

  addItem(): void {
    const array = this.itemsArray;
    if (!array) {
      return;
    }
    array.push(this.createItemGroup());
  }

  duplicateItem(index: number): void {
    const array = this.itemsArray;
    if (!array) {
      return;
    }
    const source = array.at(index)?.value;
    array.insert(index + 1, this.createItemGroup(source));
  }

  removeItem(index: number): void {
    const array = this.itemsArray;
    if (!array) {
      return;
    }
    array.removeAt(index);
  }

  handleItemKeyInput(group: FormGroup): void {
    const key = (group.get('key')?.value || '').trim();
    const pathControl = group.get('path');
    if (!pathControl) {
      return;
    }
    const currentPath = (pathControl.value || '').trim();
    if (key && !currentPath) {
      pathControl.setValue(key);
    }
  }

  private buildItemsArray(items?: VolumeItem[] | Record<string, any>): FormArray<FormGroup> {
    const normalized = this.normalizeItems(items);
    return new FormArray<FormGroup>(normalized.map(item => this.createItemGroup(item)));
  }

  private createItemGroup(item?: VolumeItem): FormGroup {
    return this.fb.group({
      key: [item?.key || '', Validators.required],
      path: [item?.path || '', Validators.required],
      mode: [item?.mode || '']
    });
  }

  private normalizeItems(items?: VolumeItem[] | Record<string, any>): VolumeItem[] {
    if (!items) {
      return [];
    }
    if (Array.isArray(items)) {
      return items.map(item => ({
        key: item?.key ?? '',
        path: item?.path ?? '',
        mode: item?.mode
      }));
    }
    if (typeof items === 'object') {
      return Object.entries(items).map(([key, value]) => {
        if (typeof value === 'object' && value !== null) {
          return {
            key,
            path: (value as any).path ?? '',
            mode: (value as any).mode
          };
        }
        return {
          key,
          path: typeof value === 'string' ? value : '',
          mode: undefined
        };
      });
    }
    return [];
  }

  private loadPersistentVolumes(): void {
    if (this.loadingPersistentVolumes) {
      return;
    }
    this.loadingPersistentVolumes = true;
    const sub = this.persistentVolumeService.getVolumes().subscribe({
      next: (volumes: PersistentVolume[]) => {
        const currentValue = this.configGroup.get('claimName')?.value;
        const mapped = volumes.map(v => ({
          label: `${v.name}${v.capacity ? ' • ' + v.capacity : ''}`,
          value: v.name
        }));
        this.persistentVolumeOptions = mapped;
        if (currentValue) {
          this.ensureClaimOption(currentValue);
          this.configGroup.get('claimName')?.setValue(currentValue, { emitEvent: false });
        }
        this.loadingPersistentVolumes = false;
      },
      error: () => {
        this.loadingPersistentVolumes = false;
      }
    });
    this.autoSaveSubscription.add(sub);
  }

  private watchClaimName(): void {
    const claimControl = this.configGroup.get('claimName');
    if (!claimControl) {
      return;
    }
    const sub = claimControl.valueChanges.subscribe((value: string) => {
      this.ensureClaimOption(value);
    });
    this.autoSaveSubscription.add(sub);
  }

  private ensureClaimOption(value: string | null | undefined): void {
    if (!value) {
      return;
    }
    const exists = this.persistentVolumeOptions.some(opt => opt.value === value);
    if (!exists) {
      this.persistentVolumeOptions = [
        ...this.persistentVolumeOptions,
        { label: value, value }
      ];
    }
  }
}
