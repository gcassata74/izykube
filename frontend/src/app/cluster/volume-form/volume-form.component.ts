import { Component, Input, OnInit, OnDestroy, OnChanges, SimpleChanges } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Volume, VolumeConfig, VolumeType } from '../../model/volume.class';
import { AutoSaveService } from '../../services/auto-save.service';
import { Subscription, tap } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { TabPanel } from 'primeng/tabview';

@Component({
  selector: 'app-volume-form',
  templateUrl: './volume-form.component.html',
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
    ['medium', 'sizeLimit', 'path', 'hostPathType', 'claimName', 'readOnly', 'name', 'secretName'].forEach(control => {
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
      case 'secret':
        configForm.addControl('secretName', this.fb.control(currentConfig.type === 'secret' ? currentConfig.secretName : '', Validators.required));
        break;
    }
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges.pipe(
      tap((changes) => console.log('Form value changed',changes))
    )
    );
  }
}


