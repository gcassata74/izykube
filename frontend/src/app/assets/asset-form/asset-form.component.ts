import { Component } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { catchError, EMPTY, filter, Subscription, tap } from 'rxjs';
import { Asset, AssetType } from 'src/app/model/asset.class';
import { AssetService } from 'src/app/services/asset.service';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-asset-form',
  templateUrl: './asset-form.component.html'
})
export class AssetFormComponent {

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
  readonly DEFAULT_BASH ="#!/bin/bash\n\n";

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
    this.assetForm = this.formBuilder.group({
      name: ['', Validators.required],
      type: [AssetType.IMAGE, Validators.required],
      yaml: [this.DEFAULT_YAML],
      bash: [this.DEFAULT_BASH],
      port: [this.DEFAULT_IMAGE_PORT, [Validators.required, Validators.min(1), Validators.max(65535)]],
      image: ['', Validators.required],
      version: [this.DEFAULT_VERSION, Validators.required]
    });

    // Subscribe to type changes
    this.assetForm.get('type')?.valueChanges.subscribe(type => {
      if (type === AssetType.PLAYBOOK) {
        this.assetForm.get('yaml')?.setValidators([Validators.required]);
        this.assetForm.patchValue({
          image: this.ANSIBLE_DEFAULT_IMAGE,
          port: this.DEFAULT_ANSIBLE_PORT,
        }, { emitEvent: false });
      } else {
        this.assetForm.get('yaml')?.clearValidators();
        this.assetForm.patchValue({
          yaml: '',
          image: '',
          port: this.DEFAULT_IMAGE_PORT
        }, { emitEvent: false });
      }
      this.assetForm.get('yaml')?.updateValueAndValidity();
    });

    this.route.paramMap.subscribe(params => {
      this.assetId = params.get('id');
      this.isEditMode = !!this.assetId;

      if (this.isEditMode && this.assetId) {
        this.loadAssetData(this.assetId);
      }
    });
  }

  loadAssetData(id: string) {
    this.subscription.add(this.assetService.getAsset(id).pipe(
      tap(data => {
        this.asset = data;
        this.assetForm.patchValue({
          name: this.asset.name,
          type: this.asset.type,
          script: this.asset.script || (this.asset.type === AssetType.PLAYBOOK) ?this.DEFAULT_YAML : this.DEFAULT_BASH,
          port: this.asset.port,
          image: this.asset.image,
          version: this.asset.version
        });
      })
    ).subscribe());
  }

  saveAsset() {
    if (this.assetForm.valid) {
      const values = this.assetForm.value;
      if (!this.isEditMode) {
        this.asset = new Asset();
      }
      this.asset.name = values.name;
      this.asset.type = values.type;
      this.asset.script = values.script;
      this.asset.port = values.port;
      this.asset.image = values.image;
      this.asset.version = values.version;

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
