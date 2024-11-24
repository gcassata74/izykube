import { Component, Input } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { catchError, filter, map, Observable, of, tap } from 'rxjs';
import { AssetType } from 'src/app/model/asset.class';
import { Job } from 'src/app/model/job.class';
import { AssetService } from 'src/app/services/asset.service';
import { AutoSaveService } from 'src/app/services/auto-save.service';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-job-form',
  templateUrl: './job-form.component.html',
  providers: [AutoSaveService]
})
export class JobFormComponent {
  @Input() selectedNode!: Job;
  form!: FormGroup;
  assets$!: Observable<any[]>;

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService,
    private assetService: AssetService,
    private notificationService: NotificationService
  ) {}

  ngOnInit() {
    this.initForm();
    this.setupAutoSave();
    this.loadAssets();
  }

  private initForm() {
    this.form = this.fb.group({
      name: [this.selectedNode.name, Validators.required],
      assetId: [this.selectedNode.assetId, Validators.required]
    });
  }

  private loadAssets() {
    this.assets$ = this.assetService.getAssets().pipe(
        map(assets => assets.filter(asset => asset.type === AssetType.PLAYBOOK)),
        tap(playbooks => console.log('Loaded playbooks:', playbooks)),
        catchError(error => {
            console.error('Error loading playbooks:', error);
            this.notificationService.error('Error', 'Failed to load playbooks');
            return of([]);
        })
    );
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }
}
