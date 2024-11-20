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

  assetForm!: FormGroup;
  isEditMode: boolean = false;
  assetId: string | null = null;
  asset!: Asset;
  subscription: Subscription = new Subscription();

  readonly ANSIBLE_DEFAULT_IMAGE = 'docker.io/ansible/ansible-runner:latest';
  readonly DEFAULT_ANSIBLE_PORT = 22;  
  readonly DEFAULT_IMAGE_PORT = 8080;
  readonly DEFAULT_VERSION = '1.00';
  readonly DEFAULT_YAML = '---\n- name: My Playbook\n  hosts: all\n  tasks:\n    - name: Example task\n      debug:\n        msg: "Hello World"';
  readonly DEFAULT_BASH = "#!/bin/bash\n\n";

  assetTypes = [
    { label: 'Playbook', value: AssetType.PLAYBOOK },
    { label: 'Image', value: AssetType.IMAGE },
    { label: 'Script', value: AssetType.SCRIPT }
  ];

  constructor(
    private formBuilder: FormBuilder,
    private assetService: AssetService,
    private router: Router,
    private route: ActivatedRoute,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.initializeForm();
    this.setupTypeChangeSubscription();
    this.handleRouteParams();
  }

  private initializeForm(): void {
    this.assetForm = this.formBuilder.group({
      name: ['', Validators.required],
      type: [AssetType.IMAGE, Validators.required],
      script: [''],  
      port: ['8080'],    
      image: [''],  
      version: [this.DEFAULT_VERSION, Validators.required]
    });

    // Set initial script value based on initial type
    this.updateScriptBasedOnType(AssetType.IMAGE);
  }

  private setupTypeChangeSubscription(): void {
    this.subscription.add(
      this.assetForm.get('type')?.valueChanges.subscribe(type => {
        this.updateFormBasedOnType(type);
      })
    );
  }

  private updateFormBasedOnType(type: AssetType): void {
    // Clear all conditional field validators first
    this.assetForm.get('script')?.clearValidators();
    this.assetForm.get('port')?.clearValidators();
    this.assetForm.get('image')?.clearValidators();

    // Set default values and validators based on type
    switch (type) {
      case AssetType.PLAYBOOK:
        this.assetForm.patchValue({
          script: this.getDefaultScriptForType(type),
          image: this.ANSIBLE_DEFAULT_IMAGE,
          port: this.DEFAULT_ANSIBLE_PORT
        });
        
        this.assetForm.get('script')?.setValidators([Validators.required]);
        this.assetForm.get('port')?.setValidators([
          Validators.required, 
          Validators.min(1), 
          Validators.max(65535)
        ]);
        this.assetForm.get('image')?.setValidators([Validators.required]);
        break;

      case AssetType.SCRIPT:
        this.assetForm.patchValue({
          script: this.getDefaultScriptForType(type),
          image: null,
          port: null
        });
        
        this.assetForm.get('script')?.setValidators([Validators.required]);
        break;

      case AssetType.IMAGE:
        this.assetForm.patchValue({
          script: null,
          image: '',
          port: this.DEFAULT_IMAGE_PORT
        });
        
        this.assetForm.get('port')?.setValidators([
          Validators.required, 
          Validators.min(1), 
          Validators.max(65535)
        ]);
        this.assetForm.get('image')?.setValidators([Validators.required]);
        break;
    }

    // Update validity of all fields
    Object.keys(this.assetForm.controls).forEach(key => {
      const control = this.assetForm.get(key);
      control?.updateValueAndValidity();
    });
  }

  private updateScriptBasedOnType(type: AssetType): void {
    
    // Only set default if current value is empty
      let defaultScript = '';
      switch (type) {
        case AssetType.PLAYBOOK:
          defaultScript = this.DEFAULT_YAML;
          break;
        case AssetType.SCRIPT:
          defaultScript = this.DEFAULT_BASH;
          break;
        default:
          defaultScript = '';
      }
      
      this.assetForm.patchValue({
        script: defaultScript
      }, { emitEvent: false });
  }

  private handleRouteParams(): void {
    this.subscription.add(
      this.route.paramMap.subscribe(params => {
        this.assetId = params.get('id');
        this.isEditMode = !!this.assetId;

        if (this.isEditMode && this.assetId) {
          this.loadAssetData(this.assetId);
        }
      })
    );
  }

  loadAssetData(id: string) {
    this.subscription.add(
      this.assetService.getAsset(id).pipe(
        tap(data => {
          this.asset = data;
          
          // First set the type to trigger type-specific logic
          this.assetForm.patchValue({
            type: this.asset.type
          }, { emitEvent: true });  // Emit event to trigger type change subscription

          // Then patch the rest of the values
          this.assetForm.patchValue({
            name: this.asset.name,
            script: this.asset.script || this.getDefaultScriptForType(this.asset.type),
            port: this.asset.port,
            image: this.asset.image,
            version: this.asset.version
          }, { emitEvent: false });
        })
      ).subscribe()
    );
  }

  private getDefaultScriptForType(type: AssetType): string {
    switch (type) {
      case AssetType.PLAYBOOK:
        return this.DEFAULT_YAML;
      case AssetType.SCRIPT:
        return this.DEFAULT_BASH;
      default:
        return '';
    }
  }

  saveAsset() {
    if (this.assetForm.valid) {
      const values = this.assetForm.value;
      if (!this.isEditMode) {
        this.asset = new Asset();
      }
      
      Object.assign(this.asset, {
        name: values.name,
        type: values.type,
        script: values.script,
        port: values.port,
        image: values.image,
        version: values.version
      });

      this.assetService.saveAsset(this.asset).pipe(
        tap(() => {
          this.notificationService.success('Success', `Asset ${this.isEditMode ? 'updated' : 'created'} successfully`);
          this.router.navigate(['/assets']);
        }),
        catchError(error => {
          this.notificationService.error('Error', `Failed to ${this.isEditMode ? 'update' : 'create'} asset`);
          console.error('Error saving asset:', error);
          return EMPTY;
        })
      ).subscribe();
    }
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  cancel() {
    this.router.navigate(['/assets']);
  }
}