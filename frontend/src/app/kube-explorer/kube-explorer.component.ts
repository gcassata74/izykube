import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription, interval } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { NamespaceOption, NamespaceSummary } from '../model/kube-summary';
import { KubeExplorerService } from '../services/kube-explorer.service';
import { NotificationService } from '../services/notification.service';

@Component({
  selector: 'app-kube-explorer',
  templateUrl: './kube-explorer.component.html',
  styleUrls: ['./kube-explorer.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class KubeExplorerComponent implements OnInit, OnDestroy {

  namespaces: NamespaceOption[] = [];
  selectedNamespace = 'all';
  summary: NamespaceSummary | null = null;
  loading = false;
  autoRefreshEnabled = false;
  readonly refreshIntervalMs = 15000;

  private autoRefreshSubscription: Subscription | null = null;

  constructor(
    private kubeExplorerService: KubeExplorerService,
    private notificationService: NotificationService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.fetchNamespaces();
  }

  ngOnDestroy(): void {
    this.teardownAutoRefresh();
  }

  fetchNamespaces(): void {
    this.kubeExplorerService.getNamespaces().subscribe({
      next: (names) => {
        const namespaceItems = ['all', ...names].map((ns) => ({ name: ns }));
        this.namespaces = namespaceItems;
        const namespaceValues = namespaceItems.map((item) => item.name);
        if (!this.selectedNamespace || !namespaceValues.includes(this.selectedNamespace)) {
          this.selectedNamespace = 'all';
        }
        this.loadSummary();
        this.cdr.markForCheck();
      },
      error: () => {
        this.notificationService.error('Failed to load namespaces');
        this.cdr.markForCheck();
      }
    });
  }

  loadSummary(): void {
    const namespace = this.selectedNamespace || 'all';
    this.loading = true;
    this.cdr.markForCheck();
    this.kubeExplorerService
      .getNamespaceSummary(namespace)
      .pipe(finalize(() => {
        this.loading = false;
        this.cdr.markForCheck();
      }))
      .subscribe({
        next: (summary) => {
          this.summary = summary;
          this.cdr.markForCheck();
        },
        error: () => {
          this.notificationService.error('Failed to load cluster resources');
          this.cdr.markForCheck();
        }
      });
  }

  onNamespaceChange(namespace: string): void {
    this.selectedNamespace = namespace;
    this.loadSummary();
  }

  toggleAutoRefresh(): void {
    this.autoRefreshEnabled = !this.autoRefreshEnabled;
    this.cdr.markForCheck();

    if (this.autoRefreshEnabled) {
      this.autoRefreshSubscription = interval(this.refreshIntervalMs).subscribe(() => this.loadSummary());
    } else {
      this.teardownAutoRefresh();
    }
  }

  manualRefresh(): void {
    this.loadSummary();
  }

  private teardownAutoRefresh(): void {
    if (this.autoRefreshSubscription) {
      this.autoRefreshSubscription.unsubscribe();
      this.autoRefreshSubscription = null;
      this.cdr.markForCheck();
    }
  }
}
