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
          const detail = error?.error || error?.message || 'Unable to download CA certificate.';
          this.notificationService.error('Download failed', typeof detail === 'string' ? detail : undefined);
        }
      });
  }
}
