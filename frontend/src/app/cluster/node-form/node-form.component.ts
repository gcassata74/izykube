import { Node } from './../../model/node.class';
import { Store } from '@ngrx/store';
import { Component, OnDestroy, ViewContainerRef, ComponentRef, ViewChild } from '@angular/core';
import { switchMap, of, filter, tap, Subscription, mergeMap, distinct, distinctUntilChanged, take, EMPTY } from 'rxjs';
import { DiagramService } from 'src/app/services/diagram.service';
import { getNodeById } from 'src/app/store/selectors/selectors';
import { DeploymentFormComponent } from '../deployment-form/deployment-form.component';
import { ConfigMapFormComponent } from '../config-map-form/config-map-form.component';
import { PodFormComponent } from '../pod-form/pod-form.component';
import { ServiceFormComponent } from '../service-form/service-form.component';
import { IngressFormComponent } from '../ingress-form/ingress-form.component';
import { ContainerFormComponent } from '../container-form/container-form.component';
import { VolumeFormComponent } from '../volume-form/volume-form.component';


@Component({
  selector: 'app-node-form',
  templateUrl: './node-form.component.html',
  styleUrls: ['./node-form.component.scss']
})
export class NodeFormComponent implements OnDestroy {

  @ViewChild('dynamicComponentContainer', { read: ViewContainerRef }) dynamicContainer!: ViewContainerRef;
  selectedNodeType!: string;
  node!: Node;
  subscription: Subscription = new Subscription();
  formMapper: any = {
    'deployment': DeploymentFormComponent,
    'configmap': ConfigMapFormComponent,
    'pod': PodFormComponent,
    'service': ServiceFormComponent,
    'ingress':IngressFormComponent,
    'container': ContainerFormComponent,
    'volume': VolumeFormComponent,
  };

  componentRef!: ComponentRef<any>;

  constructor(
    private diagramService: DiagramService,
    private store: Store,
  ) { }

 
 ngOnInit(): void {
    this.subscription.add(
      this.diagramService.selectedNode$.pipe(
      filter((node: go.Node) => node !== null && node !== undefined),
      distinctUntilChanged((prev, curr) => prev?.data?.key === curr?.data?.key),
      switchMap((node: go.Node) => this.store.select(getNodeById(node.data.key)).pipe(
        take(1),
      )),
      filter((node: Node | undefined): node is Node => !!node),
      tap((node: Node) => this.loadForm(node))
      ).subscribe()
    );
  }

  loadForm(node: Node) {
    {
      this.node = node;
      this.dynamicContainer.clear();
      this.componentRef = this.dynamicContainer.createComponent(this.formMapper[node.kind]);
      this.componentRef.instance.selectedNode = node;
    }
  }

  ngOnDestroy(): void {
    this.componentRef?.destroy();
    this.subscription.unsubscribe();
  }

}
