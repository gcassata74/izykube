import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { catchError, EMPTY, Subscription, tap } from 'rxjs';
import { Asset, AssetType } from 'src/app/model/asset.class';
import { AssetService } from 'src/app/services/asset.service';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-asset-form',
  templateUrl: './asset-form.component.html'
})
export class AssetFormComponent implements OnInit, OnDestroy {
  readonly DEFAULT_VALUES = {
    ANSIBLE_IMAGE: 'docker.io/ansible/ansible-runner:latest',
    ANSIBLE_PORT: 22,
    IMAGE_PORT: 8080,
    VERSION: '1.00',
    YAML: '---\n- name: My Playbook\n  hosts: all\n  tasks:\n    - name: Example task\n      debug:\n        msg: "Hello World"',
    BASH: '#!/bin/bash\n\n'
  } as const;

  assetForm!: FormGroup;
  isEditMode = false;
  asset?: Asset;
  subscription = new Subscription();

  assetTypes = [
    { label: 'Playbook', value: AssetType.PLAYBOOK },
    { label: 'Image', value: AssetType.IMAGE },
    { label: 'Script', value: AssetType.SCRIPT }
  ];

  constructor(
    private fb: FormBuilder,
    private assetService: AssetService,
    private router: Router,
    private route: ActivatedRoute,
    private notify: NotificationService
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.watchTypeChanges();
    this.handleRouteParams();
  }

  private initForm(): void {
    // Initialize with base validators
    this.assetForm = this.fb.group({
      name: ['', [Validators.required]],
      type: [AssetType.IMAGE, [Validators.required]],
      script: [''],
      port: ['8080'],
      image: [''],
      version: [this.DEFAULT_VALUES.VERSION, [Validators.required]]
    });
    
    // Set initial state based on default type
    this.setFormState(AssetType.IMAGE);
  }

  private watchTypeChanges(): void {
    const typeControl = this.assetForm.get('type');
    if (typeControl) {
      this.subscription.add(
        typeControl.valueChanges.pipe(
          tap(newType => this.setFormState(newType))
        ).subscribe()
      );
    }
  }

  private handleRouteParams(): void {
    this.subscription.add(
      this.route.paramMap.pipe(
        tap(params => {
          const id = params.get('id');
          if (id) {
            this.isEditMode = true;
            this.loadAsset(id);
          }
        })
      ).subscribe()
    );
  }

  private setFormState(type: AssetType): void {
    const controls = {
      script: this.assetForm.get('script'),
      port: this.assetForm.get('port'),
      image: this.assetForm.get('image')
    };

    // Clear all validators first
    Object.values(controls).forEach(control => {
      if (control) {
        control.clearValidators();
      }
    });

    // Set new state based on type
    switch (type) {
      case AssetType.PLAYBOOK:
        this.setPlaybookState(controls);
        break;
      case AssetType.SCRIPT:
        this.setScriptState(controls);
        break;
      case AssetType.IMAGE:
        this.setImageState(controls);
        break;
    }

    // Update validity
    Object.values(controls).forEach(control => {
      if (control) {
        control.updateValueAndValidity({ emitEvent: false });
      }
    });
  }

  private setPlaybookState(controls: any): void {
    const { script, port, image } = controls;
    
    if (script && !script.value) {
      script.setValue(this.DEFAULT_VALUES.YAML, { emitEvent: false });
    }
    if (port) {
      port.setValue(this.DEFAULT_VALUES.ANSIBLE_PORT, { emitEvent: false });
      port.setValidators([Validators.required, Validators.min(1), Validators.max(65535)]);
    }
    if (image) {
      image.setValue(this.DEFAULT_VALUES.ANSIBLE_IMAGE, { emitEvent: false });
      image.setValidators([Validators.required]);
    }
    if (script) {
      script.setValidators([Validators.required]);
    }
  }

  private setScriptState(controls: any): void {
    const { script, port, image } = controls;
    
    if (script && !script.value) {
      script.setValue(this.DEFAULT_VALUES.BASH, { emitEvent: false });
    }
    if (port) {
      port.setValue(null, { emitEvent: false });
    }
    if (image) {
      image.setValue(null, { emitEvent: false });
    }
    if (script) {
      script.setValidators([Validators.required]);
    }
  }

  private setImageState(controls: any): void {
    const { script, port, image } = controls;
    
    if (script) {
      script.setValue(null, { emitEvent: false });
    }
    if (port) {
      port.setValue(this.DEFAULT_VALUES.IMAGE_PORT, { emitEvent: false });
      port.setValidators([Validators.required, Validators.min(1), Validators.max(65535)]);
    }
    if (image) {
      image.setValidators([Validators.required]);
    }
  }

  private loadAsset(id: string): void {
    this.subscription.add(
      this.assetService.getAsset(id).pipe(
        tap(asset => {
          this.asset = asset;
          
          // First set type without emitting
          this.assetForm.get('type')?.setValue(asset.type, { emitEvent: false });
          
          // Setup form state for this type
          this.setFormState(asset.type);
          
          // Then patch all values
          this.assetForm.patchValue({
            name: asset.name,
            script: asset.script || this.getDefaultScript(asset.type),
            port: asset.port,
            image: asset.image,
            version: asset.version
          }, { emitEvent: false });
        }),
        catchError(error => {
          this.notify.error('Error', 'Failed to load asset');
          console.error('Error loading asset:', error);
          return EMPTY;
        })
      ).subscribe()
    );
  }

  private getDefaultScript(type: AssetType): string {
    switch (type) {
      case AssetType.PLAYBOOK:
        return this.DEFAULT_VALUES.YAML;
      case AssetType.SCRIPT:
        return this.DEFAULT_VALUES.BASH;
      default:
        return '';
    }
  }

  saveAsset(): void {
    if (this.assetForm.valid) {
      const formValue = this.assetForm.value;
      const asset = this.isEditMode ? this.asset! : new Asset();
      
      Object.assign(asset, {
        name: formValue.name,
        type: formValue.type,
        script: formValue.script,
        port: formValue.port,
        image: formValue.image,
        version: formValue.version
      });

      this.subscription.add(
        this.assetService.saveAsset(asset).pipe(
          tap(() => {
            this.notify.success('Success', `Asset ${this.isEditMode ? 'updated' : 'created'} successfully`);
            this.router.navigate(['/assets']);
          }),
          catchError(error => {
            this.notify.error('Error', `Failed to ${this.isEditMode ? 'update' : 'create'} asset`);
            console.error('Error saving asset:', error);
            return EMPTY;
          })
        ).subscribe()
      );
    } else {
      // Mark all fields as touched to trigger validation messages
      Object.keys(this.assetForm.controls).forEach(key => {
        const control = this.assetForm.get(key);
        if (control) {
          control.markAsTouched();
        }
      });
    }
  }

  cancel(): void {
    this.router.navigate(['/assets']);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}