import { TemplateService } from './../../services/template.service';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { ClusterService } from '../../services/cluster.service';
import { Cluster, ClusterExportMode } from '../../model/cluster.class';
import { AiAssistantService, AiExportYamlResponse, AiHelmChartExportResponse } from '../../services/ai-assistant.service';
import { catchError, defer, EMPTY, map, Observable, of, Subscription, switchMap, take, tap, timer } from 'rxjs';
import { finalize, takeWhile } from 'rxjs/operators';
import { Router } from '@angular/router';
import { ContextMenu } from 'primeng/contextmenu';
import { Store } from '@ngrx/store';
import { getClusters } from '../../store/selectors/selectors';
import { loadClusters } from 'src/app/store/actions/actions';
import { ClusterStatusEnum } from '../enum/cluster.-status-enum';
import { NotificationService } from 'src/app/services/notification.service';

type ClusterOperationType = 'deploy' | 'undeploy';
type OperationPhase = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'TIMEOUT';

interface OperationStatusUpdate {
  phase: OperationPhase;
  message: string;
}

interface ClusterOperationState {
  status: OperationPhase;
  action: ClusterOperationType;
  message: string;
  error?: string;
  startedAt: number;
}

@Component({
  selector: 'app-cluster-list',
  templateUrl: './cluster-list.component.html',
  styleUrls: ['./cluster-list.component.scss']
})
export class ClusterListComponent implements OnInit, OnDestroy {

  clusters$!: Observable<Cluster[]>;
  @ViewChild('cm') contextMenu!: ContextMenu;
  cols!: any[];
  items!: MenuItem[];
  selectedId!: string;
  exportingClusterId: string | null = null;
  exportingMode: ClusterExportMode | null = null;
  operationState: Record<string, ClusterOperationState | undefined> = {};
  private readonly POLL_INTERVAL_MS = 2500;
  private readonly OPERATION_TIMEOUT_MS = 120000;
  private readonly MAX_NETWORK_ERRORS = 3;
  private readonly RESULT_VISIBILITY_MS = 5000;
  private subscriptions = new Subscription();
  private cleanupTimers = new Map<string, number>();

  constructor(
    private clusterService: ClusterService,
    private templateService: TemplateService,
    private notificationService: NotificationService,
    private aiAssistantService: AiAssistantService,
    private router: Router,
    private store: Store
  ) {}

  ngOnInit() {
    this.getAllClusters();

    this.cols = [
      { field: 'name', header: 'Diagram' },
      { field: 'nameSpace', header: 'Namespace' },
      { field: 'status', header: 'Status' }
    ];
  }

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
    this.cleanupTimers.forEach(timerId => clearTimeout(timerId));
    this.cleanupTimers.clear();
  }

  private getAllClusters() {
    this.clusters$ = this.clusterService.getAllClusters().pipe(
      tap(clusters => this.store.dispatch(loadClusters({ clusters }))),
      catchError(error => {
        console.error('Error loading namespaces:', error);
        return of([]);  // Return an empty array in case of error
      }),
      switchMap(() => this.store.select(getClusters)),
      tap(clusters => console.log('Namespaces:', clusters))
    );
  }

  updateContextMenuItems($event: MouseEvent, id: string) {
    this.selectedId = id;
    this.subscriptions.add(
      this.clusters$.pipe(
        take(1),
        map(clusters => clusters.find(cluster => cluster.id === id))
      ).subscribe(
        cluster => {
          if (cluster) {
            this.items = this.generateMenuItems(cluster);
            setTimeout(() =>  { this.contextMenu.show($event); }, 100);
          }
        }
      )
    );
  }

  generateMenuItems(cluster: Cluster): MenuItem[] {
    const isBusy = this.isOperationInProgress(cluster.id ?? '');
    const canExport = cluster.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT
      || cluster.status === ClusterStatusEnum.DEPLOYED;

    return [
      { label: 'Edit', icon: 'pi pi-pencil', command: () => cluster.id !== null && this.editCluster(cluster.id) },
      { label: 'Delete Namespace', icon: 'pi pi-times', command: () => cluster.id !== null && this.deleteCluster(cluster.id) },
      {
        label: 'Create Template',
        icon: 'pi pi-th-large',
        command: () => cluster.id !== null && this.createTemplate(cluster.id),
        visible: cluster.status === ClusterStatusEnum.CREATED
      },
      {
        label: 'Delete Template',
        icon: 'pi pi-eraser',
        command: () => cluster.id !== null && this.deleteTemplate(cluster.id),
        visible: cluster.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT
      },
      {
        label: 'Deploy',
        icon: 'pi pi-play',
        disabled: isBusy,
        command: () => cluster.id !== null && this.deploy(cluster.id),
        visible: cluster.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT
      },
      {
        label: 'Undeploy',
        icon: 'pi pi-stop',
        disabled: isBusy,
        command: () => cluster.id !== null && this.undeploy(cluster.id),
        visible: cluster.status === ClusterStatusEnum.DEPLOYED
      },
      {
        label: 'Export',
        icon: 'pi pi-download',
        visible: canExport,
        items: [
          {
            label: 'YAML manifest',
            icon: 'pi pi-file',
            command: () => this.exportCluster(cluster, 'FLAT_YAML')
          },
          {
            label: 'Helm chart (.zip)',
            icon: 'pi pi-box',
            command: () => this.exportCluster(cluster, 'HELM_CHART')
          }
        ]
      }
    ];
  }

  undeploy(selectedId: string): void {
    this.executeClusterOperation('undeploy', selectedId);
  }

  deploy(selectedId: string): void {
    this.executeClusterOperation('deploy', selectedId);
  }

  createTemplate(selectedId: string): void {
    this.subscriptions.add(
      this.templateService.createTemplate(selectedId).pipe(
        tap((message: any) => {
          this.notificationService.success('Template Created', message.message as string);
          this.getAllClusters();
        }),
        catchError((error: any) => {
          this.notificationService.error('Template Creation Failed', error.error.error);
          return EMPTY;
        })
      ).subscribe()
    );
  }

  deleteTemplate(selectedId: string): void {
    this.subscriptions.add(
      this.templateService.deleteTemplate(selectedId).pipe(
        tap(() => {
          this.notificationService.success('Template deleted', 'Successfully deleted template');
          this.getAllClusters();
        }
      ),
        catchError(error => {
          this.notificationService.error('Template Deletion Failed', 'The template could not be deleted');
          return EMPTY;
        })
      ).subscribe()
    );
  }

  addCluster() {
    this.router.navigate(['cluster-form']);
  }

  editCluster(id: string) {
    this.router.navigate([`cluster-form/${id}`]);
  }

  deleteCluster(id: string): void {
    this.subscriptions.add(
      this.clusterService.deleteCluster(id).pipe(
        tap(() => {
          this.notificationService.success('Namespace Deleted', 'The namespace was successfully deleted');
          this.getAllClusters();
        }),
        catchError(error => {
          this.notificationService.error('Namespace Deletion Failed', 'The namespace could not be deleted');
          return EMPTY;
        })
      ).subscribe()
    );
  }

  editDiagram(id: string) {
    this.router.navigate([`cluster-editor/${id}`]);
  }

  onContextMenu($event: MouseEvent, id: any) {
    $event.preventDefault();
    this.updateContextMenuItems($event, id);
  }

  operationLabel(id: string): string | null {
    const state = this.operationState[id];
    if (!state) {
      return null;
    }
    if ((state.status === 'FAILED' || state.status === 'TIMEOUT') && state.error) {
      return `${state.message} - ${state.error}`;
    }
    return state.message;
  }

  operationStatus(id: string): OperationPhase | null {
    return this.operationState[id]?.status ?? null;
  }

  iconForStatus(state: OperationPhase | null): string {
    if (!state || state === 'PENDING' || state === 'RUNNING') {
      return 'pi pi-spin pi-spinner';
    }
    if (state === 'SUCCEEDED') {
      return 'pi pi-check-circle';
    }
    return 'pi pi-exclamation-circle';
  }

  statusClass(id: string): string {
    const state = this.operationState[id];
    if (!state) {
      return '';
    }
    return `operation-${state.status.toLowerCase()}`;
  }

  isOperationInProgress(id: string): boolean {
    const state = this.operationState[id];
    if (!state) {
      return false;
    }
    return state.status === 'PENDING' || state.status === 'RUNNING';
  }

  private executeClusterOperation(action: ClusterOperationType, selectedId: string): void {
    if (!selectedId) {
      return;
    }

    const labels = this.getOperationCopy(action);
    this.clearCleanupTimer(selectedId);
    this.operationState[selectedId] = {
      status: 'PENDING',
      action,
      message: labels.starting,
      startedAt: Date.now()
    };

    const request$ = action === 'deploy'
      ? this.clusterService.deploy(selectedId)
      : this.clusterService.undeploy(selectedId);

    const subscription = request$.pipe(
      tap(() => this.updateOperationState(selectedId, action, 'RUNNING', labels.running)),
      switchMap(() => this.pollClusterStatus(selectedId, action)),
      tap(update => this.handleOperationProgress(selectedId, action, update)),
      catchError(error => {
        const backendMessage = error?.error?.error || error?.message || labels.failureToast;
        this.updateOperationState(selectedId, action, 'FAILED', labels.failed, backendMessage);
        this.notificationService.error(labels.failureTitle, backendMessage);
        return EMPTY;
      }),
      finalize(() => this.scheduleCleanup(selectedId))
    ).subscribe();

    this.subscriptions.add(subscription);
  }

  private pollClusterStatus(clusterId: string, action: ClusterOperationType): Observable<OperationStatusUpdate> {
    const targetStatus = action === 'deploy' ? ClusterStatusEnum.DEPLOYED : ClusterStatusEnum.READY_FOR_DEPLOYMENT;
    const interimMessage = action === 'deploy'
      ? 'Deploying resources...'
      : 'Cleaning up resources...';
    const statusUpdate = (phase: OperationPhase, message: string): OperationStatusUpdate => ({ phase, message });

    return defer(() => {
      let consecutiveErrors = 0;
      const startedAt = Date.now();

      return timer(0, this.POLL_INTERVAL_MS).pipe(
        switchMap(() => {
          if (Date.now() - startedAt >= this.OPERATION_TIMEOUT_MS) {
            return of(statusUpdate('TIMEOUT', 'Timed out while waiting for the cluster response.'));
          }
          return this.clusterService.getCluster(clusterId).pipe(
            tap(() => consecutiveErrors = 0),
            map(cluster => {
              if (!cluster) {
                return statusUpdate('FAILED', 'Cluster could not be found anymore.');
              }

              if (cluster.status === targetStatus) {
                return statusUpdate('SUCCEEDED', 'Operation completed successfully.');
              }

              return statusUpdate('RUNNING', interimMessage);
            }),
            catchError(() => {
              consecutiveErrors += 1;
              if (consecutiveErrors >= this.MAX_NETWORK_ERRORS) {
                return of(statusUpdate('FAILED', 'Unable to reach the backend after multiple attempts.'));
              }

              return of(statusUpdate('PENDING', 'Waiting for cluster response...'));
            })
          );
        }),
        takeWhile(update => !this.isTerminalPhase(update.phase), true)
      );
    });
  }

  private handleOperationProgress(clusterId: string, action: ClusterOperationType, update: OperationStatusUpdate): void {
    const labels = this.getOperationCopy(action);

    switch (update.phase) {
      case 'RUNNING':
      case 'PENDING':
        this.updateOperationState(clusterId, action, update.phase, update.message);
        break;
      case 'SUCCEEDED':
        this.updateOperationState(clusterId, action, 'SUCCEEDED', labels.success);
        this.notificationService.success(labels.successTitle, labels.successToast);
        this.getAllClusters();
        break;
      case 'FAILED':
        this.updateOperationState(clusterId, action, 'FAILED', labels.failed, update.message);
        this.notificationService.error(labels.failureTitle, update.message);
        break;
      case 'TIMEOUT':
        this.updateOperationState(clusterId, action, 'TIMEOUT', labels.timeout, update.message);
        this.notificationService.error(labels.failureTitle, labels.timeout);
        break;
    }
  }

  private updateOperationState(
    clusterId: string,
    action: ClusterOperationType,
    status: OperationPhase,
    message: string,
    error?: string
  ): void {
    this.operationState[clusterId] = {
      status,
      action,
      message,
      error,
      startedAt: this.operationState[clusterId]?.startedAt ?? Date.now()
    };
  }

  private scheduleCleanup(clusterId: string): void {
    if (this.isOperationInProgress(clusterId)) {
      return;
    }

    this.clearCleanupTimer(clusterId);
    const timeoutId = window.setTimeout(() => {
      delete this.operationState[clusterId];
      this.cleanupTimers.delete(clusterId);
    }, this.RESULT_VISIBILITY_MS);
    this.cleanupTimers.set(clusterId, timeoutId);
  }

  private clearCleanupTimer(clusterId: string): void {
    const timerId = this.cleanupTimers.get(clusterId);
    if (timerId) {
      clearTimeout(timerId);
      this.cleanupTimers.delete(clusterId);
    }
  }

  private isTerminalPhase(phase: OperationPhase): boolean {
    return phase === 'SUCCEEDED' || phase === 'FAILED' || phase === 'TIMEOUT';
  }

  private getOperationCopy(action: ClusterOperationType) {
    if (action === 'deploy') {
      return {
        starting: 'Submitting deployment...',
        running: 'Deploying to cluster...',
        success: 'Deployed successfully',
        successTitle: 'Deployment Complete',
        successToast: 'Namespace deployment was successful',
        failed: 'Deployment failed',
        failureTitle: 'Deployment Failed',
        failureToast: 'The namespace could not be deployed',
        timeout: 'Deployment timed out'
      };
    }

    return {
      starting: 'Submitting undeployment...',
      running: 'Undeploying from cluster...',
      success: 'Undeployed successfully',
      successTitle: 'Undeployment Completed',
      successToast: 'Namespace undeployed successfully',
      failed: 'Undeployment failed',
      failureTitle: 'Undeployment Failed',
      failureToast: 'The namespace could not be undeployed',
      timeout: 'Undeployment timed out'
    };
  }

  private exportCluster(cluster: Cluster, mode: ClusterExportMode): void {
    if (!cluster?.id) {
      return;
    }
    if (this.exportingClusterId) {
      this.notificationService.warn('Export in progress', 'Wait for the current export to finish.');
      return;
    }

    const payload = JSON.parse(JSON.stringify({ ...cluster, exportMode: mode }));
    this.exportingClusterId = cluster.id;
    this.exportingMode = mode;

    const export$: Observable<AiExportYamlResponse | AiHelmChartExportResponse> = mode === 'HELM_CHART'
      ? this.aiAssistantService.exportHelmChart(payload)
      : this.aiAssistantService.exportYaml(payload);

    this.subscriptions.add(
      export$.pipe(
        finalize(() => {
          this.exportingClusterId = null;
          this.exportingMode = null;
        })
      ).subscribe({
        next: (response: AiExportYamlResponse | AiHelmChartExportResponse) => {
          if (mode === 'HELM_CHART') {
            this.handleHelmExport(cluster, response as AiHelmChartExportResponse);
          } else {
            this.handleYamlExport(cluster, response as AiExportYamlResponse);
          }
        },
        error: (error: any) => {
          const detail = error?.error || error?.message || 'Namespace export failed.';
          this.notificationService.error('Export failed', typeof detail === 'string' ? detail : undefined);
        }
      })
    );
  }

  private handleYamlExport(cluster: Cluster, response: AiExportYamlResponse): void {
    if (!response?.yaml) {
      this.notificationService.warn('No YAML returned', 'The export response was empty.');
      return;
    }
    const fileName = `${this.sanitizeFileName(cluster?.name || 'izykube-namespace')}.yaml`;
    this.downloadBlob(new Blob([response.yaml], { type: 'text/yaml;charset=utf-8' }), fileName);
    this.notificationService.success('YAML ready', `${fileName} downloaded.`);
  }

  private handleHelmExport(cluster: Cluster, response: AiHelmChartExportResponse): void {
    if (!response?.blob) {
      this.notificationService.warn('No chart returned', 'The Helm export response was empty.');
      return;
    }
    const fallbackName = `${this.sanitizeFileName(cluster?.name || 'izykube-namespace')}-chart.zip`;
    const fileName = response.fileName || fallbackName;
    this.downloadBlob(response.blob, fileName);
    this.notificationService.success('Helm chart ready', `${fileName} downloaded.`);
  }

  private downloadBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(url);
  }

  private sanitizeFileName(value: string): string {
    return value
      .toLowerCase()
      .replace(/[^a-z0-9-]+/g, '-')
      .replace(/^-+|-+$/g, '') || 'izykube-namespace';
  }
}
