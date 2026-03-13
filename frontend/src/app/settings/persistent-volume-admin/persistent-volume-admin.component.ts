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

import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { finalize, Subscription } from 'rxjs';
import { PersistentVolume } from '../../model/persistent-volume.class';
import { NotificationService } from '../../services/notification.service';
import { PersistentVolumeService } from '../../services/persistent-volume.service';
import { SelectItem } from 'primeng/api';

interface AccessModeState {
  rwo: boolean;
  rwx: boolean;
  rox: boolean;
}

@Component({
  selector: 'app-persistent-volume-admin',
  templateUrl: './persistent-volume-admin.component.html',
  styleUrls: ['./persistent-volume-admin.component.scss']
})
export class PersistentVolumeAdminComponent implements OnInit, OnDestroy {
  volumes: PersistentVolume[] = [];
  loading = false;
  dialogVisible = false;
  form!: FormGroup;
  editing?: PersistentVolume;
  private subscriptions = new Subscription();

  reclaimPolicies: SelectItem[] = [
    { label: $localize`:@@persistentVolume.policy.retain:Retain`, value: 'Retain' },
    { label: $localize`:@@persistentVolume.policy.delete:Delete`, value: 'Delete' },
    { label: $localize`:@@persistentVolume.policy.recycle:Recycle`, value: 'Recycle' }
  ];
  volumeModes: SelectItem[] = [
    { label: $localize`:@@persistentVolume.mode.filesystem:Filesystem`, value: 'Filesystem' },
    { label: $localize`:@@persistentVolume.mode.block:Block`, value: 'Block' }
  ];

  constructor(
    private fb: FormBuilder,
    private persistentVolumeService: PersistentVolumeService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.buildForm();
    this.loadVolumes();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  loadVolumes(): void {
    this.loading = true;
    this.subscriptions.add(
      this.persistentVolumeService.getVolumes()
        .pipe(finalize(() => (this.loading = false)))
        .subscribe({
          next: volumes => this.volumes = volumes,
          error: () => this.notificationService.error(
            $localize`:@@persistentVolume.error.loadTitle:Could not load persistent volumes`,
            $localize`:@@persistentVolume.error.loadDetail:Verify cluster connectivity`
          )
        })
    );
  }

  openCreate(): void {
    this.editing = undefined;
    this.form.reset({
      name: '',
      storageClassName: '',
      capacity: '10Gi',
      reclaimPolicy: 'Retain',
      volumeMode: 'Filesystem',
      path: '/data',
      accessModes: {
        rwo: true,
        rwx: false,
        rox: false
      } satisfies AccessModeState
    });
    this.form.get('name')?.enable();
    this.dialogVisible = true;
  }

  openEdit(volume: PersistentVolume): void {
    this.editing = volume;
    this.form.reset({
      name: volume.name,
      storageClassName: volume.storageClassName || '',
      capacity: volume.capacity || '',
      reclaimPolicy: volume.reclaimPolicy || 'Retain',
      volumeMode: volume.volumeMode || 'Filesystem',
      path: volume.path || '',
      accessModes: this.resolveAccessModes(volume.accessModes)
    });
    this.form.get('name')?.disable();
    this.dialogVisible = true;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const request = this.mapFormToRequest();
    const action$ = this.editing
      ? this.persistentVolumeService.updateVolume(this.editing.name, request)
      : this.persistentVolumeService.createVolume(request);

    this.loading = true;
    this.subscriptions.add(
      action$
        .pipe(finalize(() => (this.loading = false)))
        .subscribe({
          next: () => {
            this.notificationService.success(
              this.editing
                ? $localize`:@@persistentVolume.success.updatedTitle:Persistent volume updated`
                : $localize`:@@persistentVolume.success.createdTitle:Persistent volume created`,
              this.editing
                ? $localize`:@@persistentVolume.success.updatedDetail:The volume configuration was saved`
                : $localize`:@@persistentVolume.success.createdDetail:The volume is now available cluster-wide`
            );
            this.dialogVisible = false;
            this.loadVolumes();
          },
          error: () => this.notificationService.error(
            $localize`:@@persistentVolume.error.saveTitle:Unable to save persistent volume`,
            $localize`:@@persistentVolume.error.saveDetail:Check the values and try again`
          )
        })
    );
  }

  delete(volume: PersistentVolume): void {
    this.loading = true;
    this.subscriptions.add(
      this.persistentVolumeService.deleteVolume(volume.name)
        .pipe(finalize(() => (this.loading = false)))
        .subscribe({
          next: () => {
            this.notificationService.success(
              $localize`:@@persistentVolume.success.deletedTitle:Persistent volume deleted`,
              $localize`:@@persistentVolume.success.deletedDetail:${volume.name}:volumeName: was removed`
            );
            this.loadVolumes();
          },
          error: () => this.notificationService.error(
            $localize`:@@persistentVolume.error.deleteTitle:Deletion failed`,
            $localize`:@@persistentVolume.error.deleteDetail:Could not delete the persistent volume`
          )
        })
    );
  }

  get dialogTitle(): string {
    return this.editing
      ? $localize`:@@persistentVolume.dialog.edit:Edit Persistent Volume`
      : $localize`:@@persistentVolume.dialog.create:Create Persistent Volume`;
  }

  get filesystemLabel(): string {
    return $localize`:@@persistentVolume.mode.filesystem:Filesystem`;
  }

  hideDialog(): void {
    this.dialogVisible = false;
  }

  get accessModesGroup(): FormGroup {
    return this.form.get('accessModes') as FormGroup;
  }

  private buildForm(): void {
    this.form = this.fb.group({
      name: ['', Validators.required],
      storageClassName: [''],
      capacity: ['10Gi', Validators.required],
      reclaimPolicy: ['Retain'],
      volumeMode: ['Filesystem'],
      path: ['/data'],
      accessModes: this.fb.group({
        rwo: [true],
        rwx: [false],
        rox: [false]
      })
    });
  }

  private mapFormToRequest(): PersistentVolume {
    const raw = this.form.getRawValue();
    const accessModes = this.resolveSelectedAccessModes(raw.accessModes as AccessModeState);

    return {
      name: raw.name,
      storageClassName: raw.storageClassName || undefined,
      capacity: raw.capacity || undefined,
      reclaimPolicy: raw.reclaimPolicy || undefined,
      volumeMode: raw.volumeMode || undefined,
      path: raw.path || undefined,
      accessModes
    };
  }

  private resolveSelectedAccessModes(modes: AccessModeState): string[] {
    const selected: string[] = [];
    if (modes.rwo) {
      selected.push('ReadWriteOnce');
    }
    if (modes.rwx) {
      selected.push('ReadWriteMany');
    }
    if (modes.rox) {
      selected.push('ReadOnlyMany');
    }
    return selected;
  }

  private resolveAccessModes(accessModes?: string[]): AccessModeState {
    const modes = accessModes || [];
    return {
      rwo: modes.includes('ReadWriteOnce'),
      rwx: modes.includes('ReadWriteMany'),
      rox: modes.includes('ReadOnlyMany')
    };
  }
}
