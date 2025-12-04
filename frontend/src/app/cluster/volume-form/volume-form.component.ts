import { Component, Input, OnInit, OnDestroy, OnChanges, SimpleChanges } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Volume, VolumeConfig, VolumeItem, VolumeType } from '../../model/volume.class';
import { AutoSaveService } from '../../services/auto-save.service';
import { Subscription, tap } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { TabPanel } from 'primeng/tabview';

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

  volumeTypes: { label: string; value: VolumeType }[] = [
    { label: 'Empty Dir', value: 'emptyDir' },
    { label: 'Host Path', value: 'hostPath' },
    { label: 'Persistent Volume Claim', value: 'persistentVolumeClaim' },
    { label: 'Config Map', value: 'configMap' },
    { label: 'Secret', value: 'secret' }
  ];
  readonly itemSupportedTypes: VolumeType[] = ['configMap', 'secret'];

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService
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
    });
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
        configForm.addControl('claimName', this.fb.control(currentConfig.type === 'persistentVolumeClaim' ? currentConfig.claimName : '', Validators.required));
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
}
