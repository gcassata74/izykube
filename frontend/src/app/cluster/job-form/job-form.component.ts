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

import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
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
export class JobFormComponent implements OnInit, OnChanges {
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

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedNode'] && !changes['selectedNode'].firstChange) {
      this.refreshFormValues(changes['selectedNode'].currentValue as Job);
    }
  }

  private initForm() {
    this.form = this.fb.group({
      name: [this.selectedNode.name, Validators.required],
      assetId: [this.selectedNode.assetId, Validators.required]
    });
  }

  private refreshFormValues(node: Job): void {
    if (!this.form) {
      return;
    }
    this.form.patchValue({
      name: node.name,
      assetId: node.assetId
    }, { emitEvent: false });
  }

  private loadAssets() {
    this.assets$ = this.assetService.getAssets().pipe(
        map(assets => assets.filter(asset => asset.type === AssetType.PLAYBOOK || asset.type === AssetType.SCRIPT)),
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
