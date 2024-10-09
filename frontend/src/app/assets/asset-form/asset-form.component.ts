import { Component } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { catchError, EMPTY, Subscription, tap } from 'rxjs';
import { Asset } from 'src/app/model/asset.class';
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
      port: ['8080', [Validators.required, Validators.min(1), Validators.max(65535)]],
      image: ['', Validators.required],
      version: ['1.00', Validators.required]
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
    this.subscription.add(this.assetService.getAsset(id).subscribe(data => {
      this.asset = data;
      this.assetForm.patchValue({
        name: this.asset.name,
        port: this.asset.port,
        image: this.asset.image,
        version: this.asset.version
      });
    }));
  }

 
saveAsset() {
  if (this.assetForm.valid) {
    const values = this.assetForm.value;
    if (!this.isEditMode) {
      this.asset = new Asset();
    }
    this.asset.name = values.name;
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
