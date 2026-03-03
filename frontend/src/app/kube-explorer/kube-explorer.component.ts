import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Subscription, forkJoin, interval, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { NamespaceOption, NamespaceSummary } from '../model/kube-summary';
import { KubePod, KubeContainerStatus } from '../model/kube-pod';
import { KubePodEvent } from '../model/kube-pod-event';
import { KubeExplorerService } from '../services/kube-explorer.service';
import { NotificationService } from '../services/notification.service';
import { Table } from 'primeng/table';
import { KubeRowRef } from './kube-row-actions/kube-row-actions.component';
import { PortForwardResponse, PortForwardService } from '../services/port-forward.service';

type ResourceCollections = Pick<NamespaceSummary, 'pods' | 'deployments' | 'services' | 'ingresses' | 'configMaps' | 'secrets' | 'jobs' | 'cronJobs' | 'daemonSets' | 'statefulSets'>;
type ResourceKind = keyof ResourceCollections;

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
  readonly resourceMetadata: Record<ResourceKind, { label: string; icon: string; description: string }> = {
    pods: { label: 'Pods', icon: 'pi pi-box', description: 'Workload containers' },
    deployments: { label: 'Deployments', icon: 'pi pi-sitemap', description: 'Replica management' },
    services: { label: 'Services', icon: 'pi pi-share-alt', description: 'Network endpoints' },
    ingresses: { label: 'Ingresses', icon: 'pi pi-globe', description: 'Ingress routes' },
    configMaps: { label: 'ConfigMaps', icon: 'pi pi-clone', description: 'Configuration data' },
    secrets: { label: 'Secrets', icon: 'pi pi-lock', description: 'Sensitive data' },
    jobs: { label: 'Jobs', icon: 'pi pi-refresh', description: 'One-off workloads' },
    cronJobs: { label: 'CronJobs', icon: 'pi pi-calendar', description: 'Scheduled jobs' },
    daemonSets: { label: 'DaemonSets', icon: 'pi pi-server', description: 'Node daemons' },
    statefulSets: { label: 'StatefulSets', icon: 'pi pi-database', description: 'Stateful workloads' }
  };
  readonly resourceOrder: ResourceKind[] = Object.keys(this.resourceMetadata) as ResourceKind[];

  resourceMenu: ResourceMenuItem[] = [];
  activeView: ResourceKind = 'pods';
  selectedRow: any = null;
  yamlDialogVisible = false;
  yamlDialogTarget: { kind: string; namespace: string; name: string } | null = null;
  private selectedRowKeys: Partial<Record<ResourceKind, string>> = {};
  private globalFilters: Record<ResourceKind, string> = this.resourceOrder.reduce((acc, kind) => {
    acc[kind] = '';
    return acc;
  }, {} as Record<ResourceKind, string>);

  private autoRefreshSubscription: Subscription | null = null;
  private readonly autoRefreshStorageKey = 'kubeExplorer.autoRefresh';
  currentLogTarget: { type: 'pod' | 'deployment'; namespace: string; name: string } | null = null;

  logsDialogVisible = false;
  logsTitle = '';
  logsContent = '';
  logsLoading = false;
  logsError: string | null = null;
  logsPodContainers: { label: string; value: string }[] = [];
  logsSelectedContainer: string | null = null;

  inspectDialogVisible = false;
  inspectLoading = false;
  inspectError: string | null = null;
  inspectedPod: KubePod | null = null;
  inspectedEvents: KubePodEvent[] = [];
  portForwards: PortForwardResponse[] = [];
  portForwardsLoading = false;

  @ViewChild('podsTable') podsTable?: Table;
  @ViewChild('deploymentsTable') deploymentsTable?: Table;
  @ViewChild('servicesTable') servicesTable?: Table;
  @ViewChild('ingressesTable') ingressesTable?: Table;
  @ViewChild('configMapsTable') configMapsTable?: Table;
  @ViewChild('secretsTable') secretsTable?: Table;
  @ViewChild('jobsTable') jobsTable?: Table;
  @ViewChild('cronJobsTable') cronJobsTable?: Table;
  @ViewChild('daemonSetsTable') daemonSetsTable?: Table;
  @ViewChild('statefulSetsTable') statefulSetsTable?: Table;

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
    private portForwardService: PortForwardService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.autoRefreshEnabled = this.loadAutoRefreshPreference();
    if (this.autoRefreshEnabled) {
      this.startAutoRefresh();
    }
    this.fetchNamespaces();
    this.loadPortForwards();
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
        this.loadPortForwards();
        this.cdr.markForCheck();
      },
      error: () => {
        this.notificationService.error('Failed to load cluster resources');
        this.cdr.markForCheck();
        }
      });
  }

  loadPortForwards(): void {
    this.portForwardsLoading = true;
    this.portForwardService.listActiveForwards().pipe(
      finalize(() => {
        this.portForwardsLoading = false;
        this.cdr.markForCheck();
      })
    ).subscribe({
      next: (forwards) => {
        this.portForwards = forwards || [];
      },
      error: () => {
        this.portForwards = [];
      }
    });
  }

  stopPortForward(row: PortForwardResponse): void {
    if (!row) {
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
        this.loadPortForwards();
      },
      error: (error) => {
        const detail = error?.error || error?.message || 'Unable to stop port forward.';
        this.notificationService.error('Stop failed', typeof detail === 'string' ? detail : undefined);
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

  openYamlEditor(kind: string, row: { name: string; namespace: string }): void {
    if (!row?.name || !row?.namespace) {
      return;
    }
    this.yamlDialogTarget = {
      kind,
      name: row.name,
      namespace: row.namespace
    };
    this.yamlDialogVisible = true;
  }

  toggleAutoRefresh(): void {
    this.setAutoRefresh(!this.autoRefreshEnabled);
  }

  setAutoRefresh(enabled: boolean): void {
    if (this.autoRefreshEnabled === enabled) {
      return;
    }
    this.autoRefreshEnabled = enabled;
    this.persistAutoRefreshPreference(enabled);
    if (enabled) {
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
      case 'ingresses':
        return this.summary.ingresses || [];
      case 'configMaps':
        return this.summary.configMaps || [];
      case 'secrets':
        return this.summary.secrets || [];
      case 'jobs':
        return this.summary.jobs || [];
      case 'cronJobs':
        return this.summary.cronJobs || [];
      case 'daemonSets':
        return this.summary.daemonSets || [];
      case 'statefulSets':
        return this.summary.statefulSets || [];
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

  onQuickLogClick(event: MouseEvent, kind: 'pod' | 'deployment', row: any): void {
    event.stopPropagation();
    if (!row) {
      return;
    }
    if (kind === 'pod') {
      this.fetchPodLogs(row.namespace, row.name);
    } else {
      this.fetchDeploymentLogs(row.namespace, row.name);
    }
  }

  openPodLogsFromRow(row: KubeRowRef): void {
    if (!row?.namespace || !row?.name) {
      return;
    }
    this.fetchPodLogs(row.namespace, row.name);
  }

  openPodInspectFromRow(row: KubeRowRef): void {
    if (!row?.namespace || !row?.name) {
      return;
    }
    this.openPodInspect(row.namespace, row.name);
  }

  getSelectedRowEntries(): { label: string; value: string }[] {
    if (!this.selectedRow) {
      return [];
    }

    switch (this.activeView) {
      case 'ingresses':
        return [
          { label: 'Name', value: this.selectedRow.name },
          { label: 'Namespace', value: this.selectedRow.namespace },
          { label: 'Hosts', value: this.selectedRow.hosts || '-' },
          { label: 'Services', value: this.selectedRow.serviceTargets || '-' },
          { label: 'TLS', value: this.selectedRow.tls || '-' },
          { label: 'Age', value: this.selectedRow.age }
        ];
      case 'configMaps':
        return [
          { label: 'Name', value: this.selectedRow.name },
          { label: 'Namespace', value: this.selectedRow.namespace },
          { label: 'Entries', value: String(this.selectedRow.dataEntries ?? 0) },
          { label: 'Age', value: this.selectedRow.age }
        ];
      case 'secrets':
        return [
          { label: 'Name', value: this.selectedRow.name },
          { label: 'Namespace', value: this.selectedRow.namespace },
          { label: 'Type', value: this.selectedRow.type },
          { label: 'Entries', value: String(this.selectedRow.dataEntries ?? 0) },
          { label: 'Age', value: this.selectedRow.age }
        ];
      case 'jobs':
        return [
          { label: 'Name', value: this.selectedRow.name },
          { label: 'Namespace', value: this.selectedRow.namespace },
          { label: 'Completions', value: this.selectedRow.completions ?? '-' },
          { label: 'Succeeded', value: this.selectedRow.succeeded ?? '-' },
          { label: 'Failed', value: this.selectedRow.failed ?? '-' },
          { label: 'Active', value: this.selectedRow.active ?? '-' },
          { label: 'Age', value: this.selectedRow.age }
        ].map(entry => ({ label: entry.label, value: String(entry.value ?? '-') }));
      case 'cronJobs':
        return [
          { label: 'Name', value: this.selectedRow.name },
          { label: 'Namespace', value: this.selectedRow.namespace },
          { label: 'Schedule', value: this.selectedRow.schedule },
          { label: 'Suspended', value: this.selectedRow.suspended ? 'Yes' : 'No' },
          { label: 'Last Schedule', value: this.selectedRow.lastScheduleTime || '-' },
          { label: 'Active Jobs', value: String(this.selectedRow.activeJobs ?? 0) },
          { label: 'Age', value: this.selectedRow.age }
        ];
      case 'daemonSets':
        return [
          { label: 'Name', value: this.selectedRow.name },
          { label: 'Namespace', value: this.selectedRow.namespace },
          { label: 'Desired', value: this.selectedRow.desired ?? '-' },
          { label: 'Current', value: this.selectedRow.current ?? '-' },
          { label: 'Ready', value: this.selectedRow.ready ?? '-' },
          { label: 'Available', value: this.selectedRow.available ?? '-' },
          { label: 'Updated', value: this.selectedRow.updated ?? '-' },
          { label: 'Age', value: this.selectedRow.age }
        ].map(entry => ({ label: entry.label, value: String(entry.value ?? '-') }));
      case 'statefulSets':
        return [
          { label: 'Name', value: this.selectedRow.name },
          { label: 'Namespace', value: this.selectedRow.namespace },
          { label: 'Ready', value: this.selectedRow.readyReplicas ?? '-' },
          { label: 'Replicas', value: this.selectedRow.replicas ?? '-' },
          { label: 'Updated', value: this.selectedRow.updatedReplicas ?? '-' },
          { label: 'Age', value: this.selectedRow.age }
        ].map(entry => ({ label: entry.label, value: String(entry.value ?? '-') }));
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
      this.fetchPodLogsContent(this.currentLogTarget.namespace, this.currentLogTarget.name, this.logsSelectedContainer);
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

  parseReady(value: string): number {
    if (!value) {
      return 0;
    }
    const [readyRaw, totalRaw] = value.split('/');
    const ready = Number(readyRaw) || 0;
    const total = Number(totalRaw) || 0;
    if (total > 0) {
      return this.toPercent(ready, total);
    }
    return ready > 0 ? 100 : 0;
  }

  replicaPercent(item: { readyReplicas?: number; replicas?: number }): number {
    const ready = Number(item?.readyReplicas ?? 0);
    const total = Number(item?.replicas ?? 0);
    if (total > 0) {
      return this.toPercent(ready, total);
    }
    return ready > 0 ? 100 : 0;
  }

  daemonPercent(item: { ready?: number; desired?: number }): number {
    const ready = Number(item?.ready ?? 0);
    const total = Number(item?.desired ?? 0);
    if (total > 0) {
      return this.toPercent(ready, total);
    }
    return ready > 0 ? 100 : 0;
  }

  jobPercent(job: { succeeded?: number; completions?: number }): number {
    const succeeded = Number(job?.succeeded ?? 0);
    const total = Number(job?.completions ?? succeeded);
    if (total > 0) {
      return this.toPercent(succeeded, total);
    }
    return succeeded > 0 ? 100 : 0;
  }

  private teardownAutoRefresh(): void {
    if (this.autoRefreshSubscription) {
      this.autoRefreshSubscription.unsubscribe();
      this.autoRefreshSubscription = null;
      this.cdr.markForCheck();
    }
  }

  private updateResourceMenu(): void {
    this.resourceMenu = this.resourceOrder.map(kind => {
      const metadata = this.resourceMetadata[kind];
      const collection = (this.summary?.[kind] ?? []) as ResourceCollections[ResourceKind];
      const count = collection.length;
      return {
        kind,
        label: metadata.label,
        icon: metadata.icon,
        count,
        description: metadata.description
      };
    });
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
      case 'ingresses':
        return this.ingressesTable;
      case 'configMaps':
        return this.configMapsTable;
      case 'secrets':
        return this.secretsTable;
      case 'jobs':
        return this.jobsTable;
      case 'cronJobs':
        return this.cronJobsTable;
      case 'daemonSets':
        return this.daemonSetsTable;
      case 'statefulSets':
        return this.statefulSetsTable;
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

    this.kubeExplorerService.getPod(namespace, podName).pipe(
      finalize(() => this.cdr.markForCheck())
    ).subscribe({
      next: (pod) => {
        this.logsPodContainers = this.buildContainerOptions(pod);
        this.logsSelectedContainer = this.selectDefaultContainer(pod);
        this.fetchPodLogsContent(namespace, podName, this.logsSelectedContainer);
      },
      error: () => {
        this.logsPodContainers = [];
        this.logsSelectedContainer = null;
        this.fetchPodLogsContent(namespace, podName, undefined);
      }
    });
    this.cdr.markForCheck();
  }

  onLogsContainerChange(container: string): void {
    if (this.currentLogTarget?.type !== 'pod') {
      return;
    }
    this.logsSelectedContainer = container;
    this.fetchPodLogsContent(this.currentLogTarget.namespace, this.currentLogTarget.name, container);
  }

  private fetchPodLogsContent(namespace: string, podName: string, container?: string | null): void {
    this.logsLoading = true;
    this.logsError = null;
    this.logsContent = '';

    this.kubeExplorerService.getPodLogsV1(namespace, podName, container || undefined).pipe(
      finalize(() => {
        this.logsLoading = false;
        this.cdr.markForCheck();
      })
    ).subscribe({
      next: (content) => {
        const normalized = content ?? '';
        this.logsContent = normalized ? normalized : '[No log output]';
        this.logsDialogVisible = true;
      },
      error: (err) => {
        const status = err?.status;
        if (status === 404 || status === 410) {
          this.logsError = 'No logs found for this Pod.';
        } else {
          this.logsError = 'Unable to fetch pod logs.';
        }
        this.logsDialogVisible = true;
      }
    });
  }

  private openPodInspect(namespace: string, podName: string): void {
    this.inspectDialogVisible = true;
    this.inspectLoading = true;
    this.inspectError = null;
    this.inspectedPod = null;
    this.inspectedEvents = [];
    this.cdr.markForCheck();

    forkJoin({
      pod: this.kubeExplorerService.getPod(namespace, podName).pipe(catchError(() => of(null))),
      events: this.kubeExplorerService.getPodEvents(namespace, podName).pipe(catchError(() => of([]))),
    }).pipe(
      finalize(() => {
        this.inspectLoading = false;
        this.cdr.markForCheck();
      })
    ).subscribe(({ pod, events }) => {
      this.inspectedPod = pod;
      this.inspectedEvents = events || [];
      if (!pod) {
        this.inspectError = 'Unable to fetch Pod details.';
      }
    });
  }

  get inspectedContainerStatuses(): KubeContainerStatus[] {
    return this.inspectedPod?.status?.containerStatuses || [];
  }

  containerStateLabel(status: KubeContainerStatus): string {
    const state = status?.state;
    if (state?.running) {
      return `Running${state.running.startedAt ? ' • ' + state.running.startedAt : ''}`;
    }
    if (state?.waiting) {
      return `Waiting${state.waiting.reason ? ' • ' + state.waiting.reason : ''}`;
    }
    if (state?.terminated) {
      return `Terminated${state.terminated.reason ? ' • ' + state.terminated.reason : ''}`;
    }
    return 'Unknown';
  }

  private buildContainerOptions(pod: KubePod | null): { label: string; value: string }[] {
    const containers = pod?.spec?.containers || [];
    return containers
      .map(c => c?.name)
      .filter((name): name is string => !!name)
      .map(name => ({ label: name, value: name }));
  }

  private selectDefaultContainer(pod: KubePod | null): string | null {
    const containers = pod?.spec?.containers || [];
    if (!containers.length) {
      return null;
    }

    const isSidecar = (name: string, image?: string): boolean => {
      const lowered = name.toLowerCase();
      const loweredImage = (image || '').toLowerCase();
      return lowered === 'istio-proxy'
        || lowered === 'linkerd-proxy'
        || lowered === 'envoy'
        || lowered.endsWith('-proxy')
        || lowered.startsWith('istio')
        || loweredImage.includes('istio')
        || loweredImage.includes('linkerd');
    };

    const preferred = containers.find(c => c?.name && !isSidecar(c.name, c.image));
    return preferred?.name || containers[0]?.name || null;
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

  private toPercent(value: number, total: number): number {
    if (!total || total <= 0) {
      return 0;
    }
    const clamped = Math.min(Math.max(value, 0), total);
    return Math.round((clamped / total) * 100);
  }
}
