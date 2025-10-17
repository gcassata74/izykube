import { Node } from './../../model/node.class';
import { Store } from '@ngrx/store';
import { Component, OnDestroy, OnInit, Type } from '@angular/core';
import { switchMap, filter, tap, Subscription, distinctUntilChanged } from 'rxjs';
import { DiagramService } from 'src/app/services/diagram.service';
import { getNodeById } from 'src/app/store/selectors/selectors';
import { DeploymentFormComponent } from '../deployment-form/deployment-form.component';
import { ConfigMapFormComponent } from '../config-map-form/config-map-form.component';
import { PodFormComponent } from '../pod-form/pod-form.component';
import { ServiceFormComponent } from '../service-form/service-form.component';
import { IngressFormComponent } from '../ingress-form/ingress-form.component';
import { ContainerFormComponent } from '../container-form/container-form.component';
import { VolumeFormComponent } from '../volume-form/volume-form.component';
import { JobFormComponent } from '../job-form/job-form.component';
import { AssetFormComponent } from 'src/app/assets/asset-form/asset-form.component';


@Component({
  selector: 'app-node-form',
  templateUrl: './node-form.component.html',
  styleUrls: ['./node-form.component.scss']
})
export class NodeFormComponent implements OnInit, OnDestroy {

  node: Node | null = null;
  activeComponentType: Type<any> | null = null;
  componentInputs: Record<string, unknown> = {};
  private currentNodeId: string | null = null;
  subscription: Subscription = new Subscription();
  formMapper: Record<string, Type<any>> = {
    'deployment': DeploymentFormComponent,
    'configmap': ConfigMapFormComponent,
    'pod': PodFormComponent,
    'service': ServiceFormComponent,
    'ingress':IngressFormComponent,
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
      this.activeComponentType = componentType;
    }

    this.currentNodeId = node.id;
    this.componentInputs = { selectedNode: node };
  }

  private clearDynamicForm() {
    this.currentNodeId = null;
    this.node = null;
    this.activeComponentType = null;
    this.componentInputs = {};
  }

  ngOnDestroy(): void {
    this.clearDynamicForm();
    this.subscription.unsubscribe();
  }

}
