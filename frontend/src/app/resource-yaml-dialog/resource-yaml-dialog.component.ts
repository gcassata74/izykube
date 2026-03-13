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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Subscription } from 'rxjs';
import { KubeExplorerService } from '../services/kube-explorer.service';
import { NotificationService } from '../services/notification.service';

@Component({
  selector: 'app-resource-yaml-dialog',
  templateUrl: './resource-yaml-dialog.component.html',
  styleUrls: ['./resource-yaml-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResourceYamlDialogComponent implements OnChanges, OnDestroy {
  @Input() visible = false;
  @Input() kind: string | null = null;
  @Input() namespace: string | null = null;
  @Input() name: string | null = null;

  @Output() visibleChange = new EventEmitter<boolean>();

  yamlControl = new FormControl('', { nonNullable: true });
  yamlAnnotations: any[] = [];
  loading = false;
  saving = false;
  errorMessage: string | null = null;

  private subscription = new Subscription();

  constructor(
    private kubeExplorerService: KubeExplorerService,
    private notificationService: NotificationService,
    private cdr: ChangeDetectorRef
  ) {
    this.subscription.add(
      this.yamlControl.statusChanges.subscribe(() => {
        this.updateAnnotations();
      })
    );
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['visible'] || changes['kind'] || changes['namespace'] || changes['name']) && this.visible) {
      this.loadYaml();
    }
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  close(): void {
    if (this.saving) {
      return;
    }
    this.visible = false;
    this.visibleChange.emit(false);
  }

  save(): void {
    if (this.saving || this.loading) {
      return;
    }
    if (!this.kind || !this.namespace || !this.name) {
      return;
    }
    if (this.yamlControl.invalid) {
      this.yamlControl.markAsTouched();
      return;
    }
    const yaml = this.yamlControl.value?.trim();
    if (!yaml) {
      this.notificationService.warn(
        $localize`:@@resourceYamlDialog.yamlRequiredTitle:YAML required`,
        $localize`:@@resourceYamlDialog.yamlRequiredDetail:Paste YAML before saving.`
      );
      return;
    }
    this.saving = true;
    this.errorMessage = null;
    this.kubeExplorerService.updateResourceYaml(this.kind, this.namespace, this.name, yaml)
      .subscribe({
        next: (updatedYaml) => {
          this.yamlControl.setValue(updatedYaml || yaml);
          this.notificationService.success(
            $localize`:@@resourceYamlDialog.updatedTitle:Resource updated`,
            $localize`:@@resourceYamlDialog.updatedDetail:${this.kind}:kind: ${this.name}:name: patched.`
          );
          this.saving = false;
          this.cdr.markForCheck();
        },
        error: (error) => {
          const detail = error?.error || error?.message || $localize`:@@resourceYamlDialog.updateFailed:Unable to update resource.`;
          this.errorMessage = typeof detail === 'string' ? detail : $localize`:@@resourceYamlDialog.updateFailed:Unable to update resource.`;
          this.saving = false;
          this.cdr.markForCheck();
        }
      });
  }

  private loadYaml(): void {
    if (!this.kind || !this.namespace || !this.name) {
      return;
    }
    this.loading = true;
    this.errorMessage = null;
    this.kubeExplorerService.getResourceYaml(this.kind, this.namespace, this.name)
      .subscribe({
        next: (yaml) => {
          this.yamlControl.setValue(yaml || '');
          this.loading = false;
          this.cdr.markForCheck();
        },
        error: (error) => {
          const detail = error?.error || error?.message || $localize`:@@resourceYamlDialog.loadFailed:Unable to load YAML.`;
          this.errorMessage = typeof detail === 'string' ? detail : $localize`:@@resourceYamlDialog.loadFailed:Unable to load YAML.`;
          this.loading = false;
          this.cdr.markForCheck();
        }
      });
  }

  get dialogHeader(): string {
    return $localize`:@@resourceYamlDialog.title:Edit YAML`;
  }

  private updateAnnotations(): void {
    const yamlError = this.yamlControl.errors?.['yamlError'];
    if (yamlError?.line != null && yamlError?.column != null) {
      this.yamlAnnotations = [{
        row: yamlError.line,
        column: yamlError.column,
        text: yamlError.reason || $localize`:@@resourceYamlDialog.invalidYaml:Invalid YAML`,
        type: 'error'
      }];
    } else if (yamlError?.message) {
      this.yamlAnnotations = [{
        row: 0,
        column: 0,
        text: yamlError.message,
        type: 'error'
      }];
    } else {
      this.yamlAnnotations = [];
    }
    this.cdr.markForCheck();
  }
}
