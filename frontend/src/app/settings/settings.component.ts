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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { KubeExplorerService } from '../services/kube-explorer.service';
import { NotificationService } from '../services/notification.service';

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SettingsComponent {
  downloadingCa = false;

  constructor(
    private kubeExplorerService: KubeExplorerService,
    private notificationService: NotificationService,
    private cdr: ChangeDetectorRef
  ) {}

  downloadCaCertificate(): void {
    if (this.downloadingCa) {
      return;
    }
    this.downloadingCa = true;
    this.kubeExplorerService.getInternalCaCertificate()
      .pipe(finalize(() => {
        this.downloadingCa = false;
        this.cdr.markForCheck();
      }))
      .subscribe({
        next: (blob) => {
          const url = window.URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = 'izykube-ca.crt';
          link.rel = 'noopener';
          link.click();
          window.URL.revokeObjectURL(url);
        },
        error: (error) => {
          const detail = error?.error || error?.message || $localize`:@@settings.ca.download.errorDetail:Unable to download CA certificate.`;
          this.notificationService.error($localize`:@@settings.ca.download.errorTitle:Download failed`, typeof detail === 'string' ? detail : undefined);
        }
      });
  }
}
