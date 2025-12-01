import { Node } from './../../model/node.class';
import { Store } from '@ngrx/store';
import { Component, ComponentRef, OnDestroy, OnInit, Type, ViewChild, ViewContainerRef } from '@angular/core';
import { switchMap, filter, tap, Subscription, distinctUntilChanged } from 'rxjs';
import { DiagramService } from 'src/app/services/diagram.service';
import { getCurrentCluster, getNodeById } from 'src/app/store/selectors/selectors';
import { DeploymentFormComponent } from '../deployment-form/deployment-form.component';
import { ConfigMapFormComponent } from '../config-map-form/config-map-form.component';
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


@Component({
  selector: 'app-node-form',
  templateUrl: './node-form.component.html',
  styleUrls: ['./node-form.component.scss']
})
export class NodeFormComponent implements OnInit, OnDestroy {

  node: Node | null = null;
  activeComponentType: Type<any> | null = null;
  private currentNodeId: string | null = null;
  subscription: Subscription = new Subscription();
  private componentRef: ComponentRef<any> | null = null;
  @ViewChild('dynamicHost', { read: ViewContainerRef, static: true }) dynamicHost!: ViewContainerRef;
  formMapper: Record<string, Type<any>> = {
    'deployment': DeploymentFormComponent,
    'configmap': ConfigMapFormComponent,
    'secret': ConfigMapFormComponent,
    'service': ServiceFormComponent,
    'ingress': IngressFormComponent,
    'istio': IstioFormComponent,
    'container': ContainerFormComponent,
    'volume': VolumeFormComponent,
    'job': JobFormComponent,
    'asset': AssetFormComponent
  };

  constructor(
    private diagramService: DiagramService,
    private store: Store,
  ) { }

  ngOnInit(): void {
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
      if (!cluster) {
        this.updateComponentInputs({ selectedNode: node });
        return;
      }

      const sourceNodes = this.findLinkedNodes(cluster, node.id, 'incoming');
      const targetNodes = this.findLinkedNodes(cluster, node.id, 'outgoing');

      const inputs: Record<string, unknown> = { selectedNode: node };

      if (componentType === ServiceFormComponent) {
        inputs['sourceNodes'] = sourceNodes;
        inputs['targetNodes'] = targetNodes;
        inputs['cluster'] = cluster;
      } else if (componentType === IngressFormComponent || componentType === IstioFormComponent) {
        inputs['sourceNodes'] = sourceNodes;
      }

      this.updateComponentInputs(inputs);
    });
  }

  private clearDynamicForm() {
    this.currentNodeId = null;
    this.node = null;
    this.activeComponentType = null;
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

  ngOnDestroy(): void {
    this.clearDynamicForm();
    this.subscription.unsubscribe();
  }

}
