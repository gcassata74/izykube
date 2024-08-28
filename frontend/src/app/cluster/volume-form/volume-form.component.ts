import { Component, Input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AutoSaveService } from '../../services/auto-save.service';
import { Volume, VolumeType } from '../../model/volume.class';

@Component({
  selector: 'app-volume-form',
  templateUrl: './volume-form.component.html',
  styleUrls: ['./volume-form.component.scss'],
  providers: [AutoSaveService]
})
export class VolumeFormComponent implements OnInit {
  @Input() selectedNode!: Volume;
  form!: FormGroup;
  volumeTypes: VolumeType[] = ['emptyDir', 'hostPath', 'configMap', 'secret', 'persistentVolumeClaim'];

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService
  ) {}

  ngOnInit() {
    this.initForm();
    this.setupAutoSave();
  }

  private initForm() {
    this.form = this.fb.group({
      name: [this.selectedNode.name, Validators.required],
      type: [this.selectedNode.config.type, Validators.required],
      // Add more form controls based on the volume type
    });

    // React to type changes
    this.form.get('type')?.valueChanges.subscribe(type => {
      this.updateFormForVolumeType(type);
    });
  }

  private updateFormForVolumeType(type: VolumeType) {
    // Remove previous type-specific controls
    Object.keys(this.form.controls).forEach(key => {
      if (key !== 'name' && key !== 'type') {
        this.form.removeControl(key);
      }
    });

    // Add new controls based on the selected type
    switch (type) {
      case 'emptyDir':
        this.form.addControl('medium', this.fb.control(''));
        this.form.addControl('sizeLimit', this.fb.control(''));
        break;
      case 'hostPath':
        this.form.addControl('path', this.fb.control('', Validators.required));
        this.form.addControl('type', this.fb.control(''));
        break;
      // Add cases for other volume types
    }
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }
}