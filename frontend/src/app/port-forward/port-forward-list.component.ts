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
          this.notificationService.error('Unable to load port forwards');
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
          this.notificationService.success('Port forward active', `${row.serviceName} (${row.namespace})`);
          this.loadForwards();
        },
        error: (error) => {
          const detail = error?.error || error?.message || 'Unable to start port forward.';
          this.notificationService.error('Start failed', typeof detail === 'string' ? detail : undefined);
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
        this.notificationService.success('Port forward stopped', `${row.serviceName} (${row.namespace})`);
        this.loadForwards();
      },
      error: (error) => {
        const detail = error?.error || error?.message || 'Unable to stop port forward.';
        this.notificationService.error('Stop failed', typeof detail === 'string' ? detail : undefined);
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
        this.notificationService.success('Port forward removed', `${row.serviceName} (${row.namespace})`);
        this.loadForwards();
      },
      error: (error) => {
        const detail = error?.error || error?.message || 'Unable to delete port forward.';
        this.notificationService.error('Delete failed', typeof detail === 'string' ? detail : undefined);
      }
    });
  }
}
