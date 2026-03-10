import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription, finalize } from 'rxjs';
import { NotificationService } from '../services/notification.service';
import { PortForwardResponse, PortForwardService } from '../services/port-forward.service';

@Component({
  selector: 'app-port-forward-list',
  templateUrl: './port-forward-list.component.html',
  styleUrls: ['./port-forward-list.component.scss']
})
export class PortForwardListComponent implements OnInit, OnDestroy {
  forwards: PortForwardResponse[] = [];
  loading = false;
  readonly deleteTooltip = $localize`:@@common.delete:Delete`;
  private subscriptions = new Subscription();

  constructor(
    private portForwardService: PortForwardService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.loadForwards();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  loadForwards(): void {
    this.loading = true;
    this.subscriptions.add(
      this.portForwardService.listForwards().pipe(
        finalize(() => {
          this.loading = false;
        })
      ).subscribe({
        next: (items) => {
          this.forwards = items || [];
        },
        error: () => {
          this.forwards = [];
          this.notificationService.error($localize`:@@portForward.error.load:Unable to load port forwards`);
        }
      })
    );
  }

  toggleForward(row: PortForwardResponse, enabled: boolean): void {
    if (!row) {
      return;
    }
    if (enabled) {
      this.portForwardService.startForward({
        namespace: row.namespace,
        serviceName: row.serviceName,
        localPort: row.localPort,
        targetPort: row.targetPort
      }).subscribe({
        next: () => {
          this.notificationService.success(
            $localize`:@@portForward.activeTitle:Port forward active`,
            $localize`:@@portForward.rowDetail:${row.serviceName}:serviceName: (${row.namespace}:namespace:)`
          );
          this.loadForwards();
        },
        error: (error) => {
          const detail = error?.error || error?.message || $localize`:@@portForward.error.startDetail:Unable to start port forward.`;
          this.notificationService.error($localize`:@@portForward.error.startTitle:Start failed`, typeof detail === 'string' ? detail : undefined);
          this.loadForwards();
        }
      });
      return;
    }
    this.portForwardService.stopForward({
      namespace: row.namespace,
      serviceName: row.serviceName,
      localPort: row.localPort,
      targetPort: row.targetPort
    }).subscribe({
      next: () => {
        this.notificationService.success(
          $localize`:@@portForward.stoppedTitle:Port forward stopped`,
          $localize`:@@portForward.rowDetail:${row.serviceName}:serviceName: (${row.namespace}:namespace:)`
        );
        this.loadForwards();
      },
      error: (error) => {
        const detail = error?.error || error?.message || $localize`:@@portForward.error.stopDetail:Unable to stop port forward.`;
        this.notificationService.error($localize`:@@portForward.error.stopTitle:Stop failed`, typeof detail === 'string' ? detail : undefined);
        this.loadForwards();
      }
    });
  }

  deleteForward(row: PortForwardResponse): void {
    if (!row) {
      return;
    }
    this.portForwardService.deleteForward({
      namespace: row.namespace,
      serviceName: row.serviceName,
      localPort: row.localPort,
      targetPort: row.targetPort
    }).subscribe({
      next: () => {
        this.notificationService.success(
          $localize`:@@portForward.removedTitle:Port forward removed`,
          $localize`:@@portForward.rowDetail:${row.serviceName}:serviceName: (${row.namespace}:namespace:)`
        );
        this.loadForwards();
      },
      error: (error) => {
        const detail = error?.error || error?.message || $localize`:@@portForward.error.deleteDetail:Unable to delete port forward.`;
        this.notificationService.error($localize`:@@portForward.error.deleteTitle:Delete failed`, typeof detail === 'string' ? detail : undefined);
      }
    });
  }

  statusLabel(active: boolean): string {
    return active
      ? $localize`:@@portForward.status.active:Active`
      : $localize`:@@portForward.status.stopped:Stopped`;
  }
}
