import { Node } from './../../model/node.class';
import { Store } from '@ngrx/store';
import { Component, ComponentRef, OnDestroy, OnInit, Type, ViewChild, ViewContainerRef } from '@angular/core';
import { FormControl } from '@angular/forms';
import * as yaml from 'js-yaml';
import { switchMap, filter, tap, Subscription, distinctUntilChanged, interval, of, map } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { DiagramService } from 'src/app/services/diagram.service';
import { getCurrentCluster, getNodeById } from 'src/app/store/selectors/selectors';
import { DeploymentFormComponent } from '../deployment-form/deployment-form.component';
import { ConfigBundleFormComponent } from '../config-bundle-form/config-bundle-form.component';
import { ServiceFormComponent } from '../service-form/service-form.component';
import { IngressFormComponent } from '../ingress-form/ingress-form.component';
import { IstioFormComponent } from '../istio-form/istio-form.component';
import { ContainerFormComponent } from '../container-form/container-form.component';
import { VolumeFormComponent } from '../volume-form/volume-form.component';
import { JobFormComponent } from '../job-form/job-form.component';
import { AssetFormComponent } from 'src/app/assets/asset-form/asset-form.component';
import { Cluster } from 'src/app/model/cluster.class';
import { Link } from 'src/app/model/link.class';
import { take } from 'rxjs/operators';
import { ServiceAccountFormComponent } from '../service-account-form/service-account-form.component';
import { AccessPolicyFormComponent } from '../access-policy-form/access-policy-form.component';
import { KubeExplorerService } from 'src/app/services/kube-explorer.service';
import { NotificationService } from 'src/app/services/notification.service';
import { ClusterStatusEnum } from '../enum/cluster.-status-enum';
import { CustomResourceFormComponent } from '../custom-resource-form/custom-resource-form.component';


@Component({
  selector: 'app-node-form',
  templateUrl: './node-form.component.html',
  styleUrls: ['./node-form.component.scss']
})
export class NodeFormComponent implements OnInit, OnDestroy {

  node: Node | null = null;
  private currentCluster: Cluster | null = null;
  activeComponentType: Type<any> | null = null;
  private currentNodeId: string | null = null;
  subscription: Subscription = new Subscription();
  private componentRef: ComponentRef<any> | null = null;
  yamlControl = new FormControl('', { nonNullable: true });
  yamlAnnotations: any[] = [];
  yamlLoading = false;
  yamlSaving = false;
  yamlError: string | null = null;
  yamlKind: string | null = null;
  yamlNamespace: string | null = null;
  yamlName: string | null = null;
  yamlEnabled = false;
  yamlTabVisible = true;
  logsEnabled = false;
  logsTabVisible = true;
  logsLoading = false;
  logsError: string | null = null;
  logsData: { name: string; namespace: string; pods: { name: string; namespace: string; logs: string }[] } | null = null;
  logsPrevious = false;
  logsReason: string | null = null;
  logsFilter = '';
  readonly logsOptions = [
    { label: 'Current logs', value: false },
    { label: 'Previous logs', value: true }
  ];
  activeTabIndex = 0;
  private readonly logsRefreshMs = 15000;
  @ViewChild('dynamicHost', { read: ViewContainerRef, static: true }) dynamicHost!: ViewContainerRef;
  formMapper: Record<string, Type<any>> = {
    'deployment': DeploymentFormComponent,
    'configmap': ConfigBundleFormComponent,
    'configbundle': ConfigBundleFormComponent,
    'secret': ConfigBundleFormComponent,
    'service': ServiceFormComponent,
    'ingress': IngressFormComponent,
    'istio': IstioFormComponent,
    'container': ContainerFormComponent,
    'volume': VolumeFormComponent,
    'job': JobFormComponent,
    'cr': CustomResourceFormComponent,
    'serviceaccount': ServiceAccountFormComponent,
    'accesspolicy': AccessPolicyFormComponent,
    'asset': AssetFormComponent
  };

  constructor(
    private diagramService: DiagramService,
    private store: Store,
    private kubeExplorerService: KubeExplorerService,
    private notificationService: NotificationService,
  ) { }

  ngOnInit(): void {
    this.subscription.add(
      this.store.select(getCurrentCluster).pipe(
        map((cluster: Cluster) => cluster || null),
        distinctUntilChanged((prev, curr) =>
          (prev?.diagram || '') === (curr?.diagram || '') &&
          (prev?.status || null) === (curr?.status || null)
        )
      ).subscribe((cluster) => {
        this.currentCluster = cluster;
        const isYamlVisible =
          this.currentCluster?.status === ClusterStatusEnum.DEPLOYED ||
          this.currentCluster?.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT;
        this.yamlTabVisible = !!this.currentCluster?.diagram && isYamlVisible;
        this.logsTabVisible = this.currentCluster?.status === ClusterStatusEnum.DEPLOYED;
      })
    );
    this.subscription.add(
      this.yamlControl.statusChanges.subscribe(() => {
        this.updateYamlAnnotations();
      })
    );
    this.subscription.add(
      this.diagramService.selectedNodeId$.pipe(
        distinctUntilChanged(),
        tap((nodeId: string | null) => {
          if (!nodeId) {
            this.clearDynamicForm();
          }
        }),
        filter((nodeId: string | null): nodeId is string => !!nodeId),
        switchMap((nodeId: string) =>
          this.store.select(getNodeById(nodeId)).pipe(
            // wait for the store to emit the node after it has been created/updated
            filter((node: Node | undefined): node is Node => !!node)
          )
        ),
        tap((node: Node) => this.loadForm(node))
      ).subscribe()
    );

    this.subscription.add(
      interval(this.logsRefreshMs).pipe(
        switchMap(() => {
          if (this.activeTabIndex !== 2 || !this.logsEnabled) {
            return of(null);
          }
          this.logsLoading = true;
          return this.kubeExplorerService.getWorkloadLogs(
            this.resolveLogKind(this.node?.kind) || '',
            this.yamlNamespace || '',
            this.yamlName || '',
            500,
            this.logsPrevious
          ).pipe(
            catchError(() => of(null))
          );
        })
      ).subscribe((logs) => {
        if (logs) {
          this.logsData = logs;
        }
        this.logsLoading = false;
      })
    );
  }

  loadForm(node: Node) {
    this.node = node;

    const componentKey = node.kind?.toLowerCase?.() ?? node.kind;
    const componentType = this.formMapper[componentKey] || this.formMapper[node.kind];
    if (!componentType) {
      console.warn(`No form component mapped for node kind: ${node.kind}`);
      this.clearDynamicForm();
      return;
    }

    const isSameComponentType = this.activeComponentType === componentType;
    const isSameNode = node.id === this.currentNodeId;

    if (!isSameComponentType || !isSameNode) {
      this.createComponent(componentType);
    }

    this.currentNodeId = node.id;

      this.store.select(getCurrentCluster).pipe(take(1)).subscribe((cluster: Cluster) => {
        this.currentCluster = cluster || null;
        if (!cluster) {
          this.updateComponentInputs({ selectedNode: node });
          this.resetYamlContext();
          return;
        }

      const sourceNodes = this.findLinkedNodes(cluster, node.id, 'incoming');
      const targetNodes = this.findLinkedNodes(cluster, node.id, 'outgoing');

      const inputs: Record<string, unknown> = { selectedNode: node };

      if (componentType === ServiceFormComponent) {
        inputs['sourceNodes'] = sourceNodes;
        inputs['targetNodes'] = targetNodes;
        inputs['cluster'] = cluster;
      } else if (componentType === DeploymentFormComponent) {
        inputs['clusterNamespace'] = cluster?.nameSpace || 'default';
      } else if (componentType === IngressFormComponent || componentType === IstioFormComponent) {
        inputs['sourceNodes'] = sourceNodes;
      } else if (componentType === ConfigBundleFormComponent) {
        inputs['sourceNodes'] = sourceNodes;
        inputs['targetNodes'] = targetNodes;
        inputs['clusterNamespace'] = cluster?.nameSpace || 'default';
      } else if (componentType === ServiceAccountFormComponent) {
        inputs['clusterNamespace'] = cluster?.nameSpace || 'default';
      } else if (componentType === AccessPolicyFormComponent) {
        inputs['clusterNamespace'] = cluster?.nameSpace || 'default';
        inputs['clusterNodes'] = cluster?.nodes || [];
      } else if (componentType === CustomResourceFormComponent) {
        inputs['clusterNamespace'] = cluster?.nameSpace || 'default';
      }

      this.updateComponentInputs(inputs);
      this.updateYamlContext(node, cluster);
      this.updateLogsContext(node, cluster);
    });
  }

  private clearDynamicForm() {
    this.currentNodeId = null;
    this.node = null;
    this.activeComponentType = null;
    this.resetYamlContext();
    this.dynamicHost?.clear();
    if (this.componentRef) {
      this.componentRef.destroy();
      this.componentRef = null;
    }
  }

  private findLinkedNodes(cluster: Cluster, nodeId: string, direction: 'incoming' | 'outgoing'): Node[] {
    if (!cluster?.links?.length) {
      return [];
    }

    const links: Link[] = cluster.links;
    const linkedIds = direction === 'incoming'
      ? links.filter(link => link.target === nodeId).map(link => link.source)
      : links.filter(link => link.source === nodeId).map(link => link.target);

    if (!linkedIds.length) {
      return [];
    }

    return (cluster.nodes ?? []).filter((n: Node) => linkedIds.includes(n.id));
  }

  private createComponent(componentType: Type<any>): void {
    this.dynamicHost.clear();
    if (this.componentRef) {
      this.componentRef.destroy();
      this.componentRef = null;
    }
    this.componentRef = this.dynamicHost.createComponent(componentType);
    this.activeComponentType = componentType;
  }

  private updateComponentInputs(inputs: Record<string, unknown>): void {
    if (!this.componentRef) {
      return;
    }
    Object.entries(inputs).forEach(([key, value]) => {
      this.componentRef!.setInput(key, value);
    });
    this.componentRef.changeDetectorRef.detectChanges();
  }

  loadYaml(): void {
    if (!this.yamlEnabled || !this.yamlKind || !this.yamlNamespace || !this.yamlName) {
      return;
    }
    this.yamlLoading = true;
    this.yamlError = null;
    this.kubeExplorerService.getResourceYaml(this.yamlKind, this.yamlNamespace, this.yamlName).subscribe({
      next: (yaml) => {
        this.yamlControl.setValue(yaml || '');
        this.yamlLoading = false;
      },
      error: (error) => {
        const fallback = this.getTemplateYamlFallback();
        if (fallback) {
          this.yamlControl.setValue(fallback);
          this.yamlError = null;
          this.yamlLoading = false;
          return;
        }
        const detail = error?.error || error?.message || 'Unable to load YAML.';
        this.yamlError = typeof detail === 'string' ? detail : 'Unable to load YAML.';
        this.yamlLoading = false;
      }
    });
  }

  saveYaml(): void {
    if (this.yamlSaving || this.yamlLoading || !this.yamlEnabled) {
      return;
    }
    if (!this.yamlKind || !this.yamlNamespace || !this.yamlName) {
      return;
    }
    if (this.yamlControl.invalid) {
      this.yamlControl.markAsTouched();
      return;
    }
    const yaml = this.yamlControl.value?.trim();
    if (!yaml) {
      this.notificationService.warn('YAML required', 'Paste YAML before saving.');
      return;
    }
    this.yamlSaving = true;
    this.yamlError = null;
    this.kubeExplorerService.updateResourceYaml(this.yamlKind, this.yamlNamespace, this.yamlName, yaml).subscribe({
      next: (updated) => {
        this.yamlControl.setValue(updated || yaml);
        this.yamlSaving = false;
        this.notificationService.success('Resource updated', `${this.yamlKind} ${this.yamlName} patched.`);
      },
      error: (error) => {
        const detail = error?.error || error?.message || 'Unable to update resource.';
        this.yamlError = typeof detail === 'string' ? detail : 'Unable to update resource.';
        this.yamlSaving = false;
      }
    });
  }

  onTabChange(event: { index: number }): void {
    this.activeTabIndex = event.index;
    if (event.index === 2) {
      this.loadLogs();
    }
  }

  loadLogs(): void {
    if (!this.logsEnabled || !this.yamlNamespace || !this.yamlName) {
      return;
    }
    const kind = this.resolveLogKind(this.node?.kind);
    if (!kind) {
      return;
    }
    this.logsLoading = true;
    this.logsError = null;
    this.logsData = null;
    this.kubeExplorerService.getWorkloadLogs(kind, this.yamlNamespace, this.yamlName, 500, this.logsPrevious).subscribe({
      next: (logs) => {
        this.logsData = logs;
        this.logsLoading = false;
      },
      error: (error) => {
        const detail = error?.error || error?.message || 'Unable to load logs.';
        this.logsError = typeof detail === 'string' ? detail : 'Unable to load logs.';
        this.logsLoading = false;
      }
    });
  }

  onLogsOptionChange(value: boolean): void {
    this.logsPrevious = value;
    this.loadLogs();
  }

  onLogsFilterChange(value: string): void {
    this.logsFilter = value || '';
  }

  getFilteredPods(): { name: string; namespace: string; logs: string }[] {
    const pods = this.logsData?.pods || [];
    if (!this.logsFilter.trim()) {
      return pods;
    }
    return pods
      .map(pod => ({ ...pod, logs: this.filterLogText(pod.logs) }))
      .filter(pod => pod.logs.trim().length > 0);
  }

  private updateYamlContext(node: Node, cluster: Cluster): void {
    const kind = this.resolveYamlKind(node.kind);
    const namespace = cluster?.nameSpace || null;
    this.yamlKind = kind;
    this.yamlNamespace = namespace;
    this.yamlName = node?.name || null;
    this.yamlEnabled = !!kind && !!namespace && !!this.yamlName;
    this.yamlError = null;
    const isYamlVisible =
      this.currentCluster?.status === ClusterStatusEnum.DEPLOYED ||
      this.currentCluster?.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT;
    this.yamlTabVisible = !!this.currentCluster?.diagram && isYamlVisible;
    this.logsTabVisible = this.currentCluster?.status === ClusterStatusEnum.DEPLOYED;

    if (this.yamlEnabled) {
      this.loadYaml();
    } else {
      this.yamlControl.setValue('');
    }
  }

  private updateLogsContext(node: Node, cluster: Cluster): void {
    const namespace = cluster?.nameSpace || null;
    const isDeployed = cluster?.status === ClusterStatusEnum.DEPLOYED;
    const kind = this.resolveLogKind(node.kind);
    this.logsEnabled = !!kind && !!namespace && isDeployed;
    this.logsError = null;
    this.logsData = null;
    this.logsReason = null;
    this.logsPrevious = false;

    if (!this.logsEnabled || !namespace || !node?.name) {
      return;
    }

    this.kubeExplorerService.getWorkloadHealth(namespace).subscribe({
      next: (health) => {
        const entry = health.find(item => item.kind === kind && item.name === node.name);
        if (entry?.reason === 'CrashLoopBackOff') {
          this.logsPrevious = true;
          this.logsReason = entry.reason || null;
        }
        if (this.activeTabIndex === 2) {
          this.loadLogs();
        }
      },
      error: () => {
        if (this.activeTabIndex === 2) {
          this.loadLogs();
        }
      }
    });
  }

  private resetYamlContext(): void {
    this.yamlKind = null;
    this.yamlNamespace = null;
    this.yamlName = null;
    this.yamlEnabled = false;
    this.yamlError = null;
    this.yamlControl.setValue('');
    const isYamlVisible =
      this.currentCluster?.status === ClusterStatusEnum.DEPLOYED ||
      this.currentCluster?.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT;
    this.yamlTabVisible = !!this.currentCluster?.diagram && isYamlVisible;
    this.logsTabVisible = this.currentCluster?.status === ClusterStatusEnum.DEPLOYED;
    this.logsEnabled = false;
    this.logsError = null;
    this.logsData = null;
    this.logsPrevious = false;
    this.logsReason = null;
  }

  private resolveYamlKind(kind?: string): string | null {
    const normalized = (kind || '').toLowerCase().trim();
    if (!normalized) {
      return null;
    }
    if (normalized === 'configbundle') {
      return 'configmap';
    }
    return normalized;
  }

  private getTemplateYamlFallback(): string | null {
    if (!this.currentCluster?.diagram || !this.yamlKind || !this.yamlName) {
      return null;
    }
    try {
      const diagram = JSON.parse(this.currentCluster.diagram);
      const rawManifests = Array.isArray(diagram?.rawManifests) ? diagram.rawManifests : [];
      const normalizedKind = this.yamlKind.toLowerCase();
      const normalizedName = this.yamlName;
      const entry = rawManifests.find((item: any) => {
        const kind = String(item?.kind || '').toLowerCase();
        const name = String(item?.name || '');
        return kind === normalizedKind && name === normalizedName;
      });
      if (!entry?.manifest) {
        return null;
      }
      return yaml.dump(entry.manifest, { noRefs: true, lineWidth: 120 });
    } catch {
      return null;
    }
  }

  private resolveLogKind(kind?: string): string | null {
    const normalized = (kind || '').toLowerCase();
    if (normalized === 'deployment' || normalized === 'statefulset' || normalized === 'daemonset') {
      return normalized;
    }
    return null;
  }

  private filterLogText(logs: string): string {
    const query = this.logsFilter.trim().toLowerCase();
    if (!query) {
      return logs || '';
    }
    return (logs || '')
      .split('\n')
      .filter(line => line.toLowerCase().includes(query))
      .join('\n');
  }

  private updateYamlAnnotations(): void {
    const yamlError = this.yamlControl.errors?.['yamlError'];
    if (yamlError?.line != null && yamlError?.column != null) {
      this.yamlAnnotations = [{
        row: yamlError.line,
        column: yamlError.column,
        text: yamlError.reason || 'Invalid YAML',
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
  }

  ngOnDestroy(): void {
    this.clearDynamicForm();
    this.subscription.unsubscribe();
  }

}
