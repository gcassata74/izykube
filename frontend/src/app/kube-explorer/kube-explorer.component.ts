import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Subscription, interval } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { NamespaceOption, NamespaceSummary } from '../model/kube-summary';
import { KubeExplorerService } from '../services/kube-explorer.service';
import { NotificationService } from '../services/notification.service';
import { Table } from 'primeng/table';

type ResourceKind = 'pods' | 'deployments' | 'services';

interface ResourceMenuItem {
  kind: ResourceKind;
  label: string;
  icon: string;
  count: number;
  description: string;
}

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
  readonly resourceOrder: ResourceKind[] = ['pods', 'deployments', 'services'];

  resourceMenu: ResourceMenuItem[] = [];
  activeView: ResourceKind = 'pods';
  selectedRow: any = null;
  private selectedRowKeys: Partial<Record<ResourceKind, string>> = {};
  private globalFilters: Record<ResourceKind, string> = {
    pods: '',
    deployments: '',
    services: ''
  };

  private autoRefreshSubscription: Subscription | null = null;
  private readonly autoRefreshStorageKey = 'kubeExplorer.autoRefresh';
  currentLogTarget: { type: 'pod' | 'deployment'; namespace: string; name: string } | null = null;

  logsDialogVisible = false;
  logsTitle = '';
  logsContent = '';
  logsLoading = false;
  logsError: string | null = null;

  @ViewChild('podsTable') podsTable?: Table;
  @ViewChild('deploymentsTable') deploymentsTable?: Table;
  @ViewChild('servicesTable') servicesTable?: Table;

  podStatusClass = (pod: any): string => {
    const status = (pod?.status || '').toLowerCase();

    if (status.includes('running')) {
      return 'row--ok';
    }

    if (status.includes('pending')) {
      return 'row--warn';
    }

    if (status.includes('failed') || status.includes('error') || status.includes('crash')) {
      return 'row--danger';
    }

    return '';
  };

  deploymentStatusClass = (deployment: any): string => {
    if (!deployment) {
      return '';
    }

    const ready = Number(deployment.readyReplicas ?? 0);
    const desired = Number(deployment.replicas ?? 0);
    const available = Number(deployment.availableReplicas ?? 0);

    if (desired > 0 && ready >= desired) {
      return 'row--ok';
    }

    if (available === 0) {
      return 'row--danger';
    }

    return 'row--warn';
  };

  serviceTypeClass = (service: any): string => {
    const type = (service?.type || '').toLowerCase();

    if (type === 'loadbalancer') {
      return 'row--ok';
    }

    if (type === 'nodeport') {
      return 'row--warn';
    }

    return '';
  };

  constructor(
    private kubeExplorerService: KubeExplorerService,
    private notificationService: NotificationService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.autoRefreshEnabled = this.loadAutoRefreshPreference();
    if (this.autoRefreshEnabled) {
      this.startAutoRefresh();
    }
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
        this.updateResourceMenu();
        this.restoreSelectionForActiveView();
        this.reapplyFilterForActiveView();
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

  selectResource(kind: ResourceKind): void {
    if (this.activeView === kind) {
      return;
    }
    this.activeView = kind;
    this.selectedRow = null;
    this.restoreSelectionForActiveView();
    this.delayReapplyFilter();
    this.cdr.markForCheck();
  }

  onGlobalFilterChange(value: string): void {
    this.globalFilters[this.activeView] = value;
    const table = this.getActiveTable();
    table?.filterGlobal(value, 'contains');
  }

  onRowSelect(row: any): void {
    this.selectedRow = row;
    const key = row?.name;
    if (key) {
      this.selectedRowKeys[this.activeView] = key;
    }
    this.cdr.markForCheck();
  }

  clearRowSelection(): void {
    this.selectedRow = null;
    delete this.selectedRowKeys[this.activeView];
    const table = this.getActiveTable();
    if (table) {
      table.selection = null;
    }
    this.delayReapplyFilter();
    this.cdr.markForCheck();
  }

  toggleAutoRefresh(): void {
    this.autoRefreshEnabled = !this.autoRefreshEnabled;
    this.persistAutoRefreshPreference(this.autoRefreshEnabled);
    if (this.autoRefreshEnabled) {
      this.startAutoRefresh();
    } else {
      this.teardownAutoRefresh();
    }
    this.cdr.markForCheck();
  }

  manualRefresh(): void {
    this.loadSummary();
  }

  get activeDataset(): any[] {
    if (!this.summary) {
      return [];
    }
    switch (this.activeView) {
      case 'deployments':
        return this.summary.deployments || [];
      case 'services':
        return this.summary.services || [];
      case 'pods':
      default:
        return this.summary.pods || [];
    }
  }

  get activeLabel(): string {
    const item = this.resourceMenu.find(menuItem => menuItem.kind === this.activeView);
    return item ? item.label : this.activeView;
  }

  get activeFilterValue(): string {
    return this.globalFilters[this.activeView];
  }

  getSelectedRowEntries(): { label: string; value: string }[] {
    if (!this.selectedRow) {
      return [];
    }

    switch (this.activeView) {
      case 'deployments':
        return [
          { label: 'Name', value: this.selectedRow.name },
          { label: 'Namespace', value: this.selectedRow.namespace },
          { label: 'Ready', value: String(this.selectedRow.readyReplicas) },
          { label: 'Desired', value: String(this.selectedRow.replicas) },
          { label: 'Updated', value: String(this.selectedRow.updatedReplicas) },
          { label: 'Available', value: String(this.selectedRow.availableReplicas) },
          { label: 'Age', value: this.selectedRow.age }
        ];
      case 'services':
        return [
          { label: 'Name', value: this.selectedRow.name },
          { label: 'Namespace', value: this.selectedRow.namespace },
          { label: 'Type', value: this.selectedRow.type },
          { label: 'Cluster IP', value: this.selectedRow.clusterIp || '-' },
          { label: 'External IP', value: this.selectedRow.externalIp || '-' },
          { label: 'Ports', value: this.selectedRow.ports || '-' },
          { label: 'Age', value: this.selectedRow.age }
        ];
      case 'pods':
      default:
        return [
          { label: 'Name', value: this.selectedRow.name },
          { label: 'Namespace', value: this.selectedRow.namespace },
          { label: 'Status', value: this.selectedRow.status },
          { label: 'Ready', value: String(this.selectedRow.ready) },
          { label: 'Restarts', value: String(this.selectedRow.restarts) },
          { label: 'Node', value: this.selectedRow.node || '-' },
          { label: 'Age', value: this.selectedRow.age }
        ];
    }
  }

  get canViewLogs(): boolean {
    return !!this.selectedRow && (this.activeView === 'pods' || this.activeView === 'deployments');
  }

  viewLogs(): void {
    if (!this.selectedRow || !this.canViewLogs) {
      return;
    }
    if (this.activeView === 'pods') {
      this.fetchPodLogs(this.selectedRow.namespace, this.selectedRow.name);
    } else if (this.activeView === 'deployments') {
      this.fetchDeploymentLogs(this.selectedRow.namespace, this.selectedRow.name);
    }
  }

  refreshLogs(): void {
    if (!this.currentLogTarget) {
      return;
    }
    if (this.currentLogTarget.type === 'pod') {
      this.fetchPodLogs(this.currentLogTarget.namespace, this.currentLogTarget.name, false);
    } else {
      this.fetchDeploymentLogs(this.currentLogTarget.namespace, this.currentLogTarget.name, false);
    }
  }

  copyLogs(): void {
    if (!this.logsContent) {
      return;
    }
    if (navigator?.clipboard) {
      navigator.clipboard.writeText(this.logsContent).then(() => {
        this.notificationService.success('Logs copied', 'Log output copied to clipboard.');
      }, () => {
        this.notificationService.warn('Copy failed', 'Unable to copy logs to clipboard.');
      });
    }
  }

  private teardownAutoRefresh(): void {
    if (this.autoRefreshSubscription) {
      this.autoRefreshSubscription.unsubscribe();
      this.autoRefreshSubscription = null;
      this.cdr.markForCheck();
    }
  }

  private updateResourceMenu(): void {
    const podsCount = this.summary?.pods?.length ?? 0;
    const deploymentsCount = this.summary?.deployments?.length ?? 0;
    const servicesCount = this.summary?.services?.length ?? 0;

    this.resourceMenu = [
      {
        kind: 'pods',
        label: 'Pods',
        icon: 'pi pi-box',
        count: podsCount,
        description: 'Workload containers'
      },
      {
        kind: 'deployments',
        label: 'Deployments',
        icon: 'pi pi-sitemap',
        count: deploymentsCount,
        description: 'Replica management'
      },
      {
        kind: 'services',
        label: 'Services',
        icon: 'pi pi-share-alt',
        count: servicesCount,
        description: 'Network endpoints'
      }
    ];
  }

  private restoreSelectionForActiveView(): void {
    const key = this.selectedRowKeys[this.activeView];
    if (!key) {
      this.selectedRow = null;
      return;
    }

    const nextRow = this.activeDataset.find(item => item?.name === key) || null;
    this.selectedRow = nextRow;
  }

  private reapplyFilterForActiveView(): void {
    const value = this.globalFilters[this.activeView];
    if (!value) {
      const table = this.getActiveTable();
      table?.filterGlobal('', 'contains');
      return;
    }
    this.delayReapplyFilter();
  }

  private delayReapplyFilter(): void {
    const value = this.globalFilters[this.activeView];
    setTimeout(() => {
      const table = this.getActiveTable();
      table?.filterGlobal(value, 'contains');
    });
  }

  private getActiveTable(): Table | undefined {
    switch (this.activeView) {
      case 'deployments':
        return this.deploymentsTable;
      case 'services':
        return this.servicesTable;
      case 'pods':
      default:
        return this.podsTable;
    }
  }

  private startAutoRefresh(): void {
    this.teardownAutoRefresh();
    this.autoRefreshSubscription = interval(this.refreshIntervalMs).subscribe(() => this.loadSummary());
  }

  private persistAutoRefreshPreference(value: boolean): void {
    if (typeof window === 'undefined') {
      return;
    }
    if (value) {
      localStorage.setItem(this.autoRefreshStorageKey, 'true');
    } else {
      localStorage.removeItem(this.autoRefreshStorageKey);
    }
  }

  private loadAutoRefreshPreference(): boolean {
    if (typeof window === 'undefined') {
      return false;
    }
    return localStorage.getItem(this.autoRefreshStorageKey) === 'true';
  }

  private fetchPodLogs(namespace: string, podName: string, showDialog = true): void {
    if (!namespace || !podName) {
      return;
    }
    this.currentLogTarget = { type: 'pod', namespace, name: podName };
    if (showDialog) {
      this.prepareLogsDialog(`Pod logs • ${podName}`);
    }
    this.logsLoading = true;
    this.logsError = null;
    this.logsContent = '';
    this.kubeExplorerService.getPodLogs(namespace, podName).pipe(
      finalize(() => {
        this.logsLoading = false;
        this.cdr.markForCheck();
      })
    ).subscribe({
      next: (response) => {
        this.logsContent = response?.logs || '[No log output]';
        this.logsDialogVisible = true;
      },
      error: () => {
        this.logsError = 'Unable to fetch pod logs.';
      }
    });
    this.cdr.markForCheck();
  }

  private fetchDeploymentLogs(namespace: string, deploymentName: string, showDialog = true): void {
    if (!namespace || !deploymentName) {
      return;
    }
    this.currentLogTarget = { type: 'deployment', namespace, name: deploymentName };
    if (showDialog) {
      this.prepareLogsDialog(`Deployment logs • ${deploymentName}`);
    }
    this.logsLoading = true;
    this.logsError = null;
    this.logsContent = '';
    this.kubeExplorerService.getDeploymentLogs(namespace, deploymentName).pipe(
      finalize(() => {
        this.logsLoading = false;
        this.cdr.markForCheck();
      })
    ).subscribe({
      next: (response) => {
        if (!response?.pods?.length) {
          this.logsContent = 'No pods found for this deployment.';
        } else {
          this.logsContent = response.pods.map(pod => {
            const log = pod.logs || '[No log output]';
            return `=== Pod ${pod.name} ===\n${log}`;
          }).join('\n\n');
        }
        this.logsDialogVisible = true;
      },
      error: () => {
        this.logsError = 'Unable to fetch deployment logs.';
      }
    });
    this.cdr.markForCheck();
  }

  private prepareLogsDialog(title: string): void {
    this.logsTitle = title;
    this.logsDialogVisible = true;
    this.logsContent = '';
    this.logsError = null;
  }
}
