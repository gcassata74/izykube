import { Node } from './../../model/node.class';
import { Store } from '@ngrx/store';
import { Component, OnDestroy, ViewContainerRef, ComponentRef } from '@angular/core';
import { switchMap, of, filter, tap, Subscription } from 'rxjs';
import { DiagramService } from 'src/app/services/diagram.service';
import { getNodeById } from 'src/app/store/selectors/selectors';
import { DeploymentFormComponent } from '../deployment-form/deployment-form.component';
import { ConfigMapFormComponent } from '../config-map-form/config-map-form.component';
import { PodFormComponent } from '../pod-form/pod-form.component';


@Component({
  selector: 'app-node-form',
  template: '',
})
export class NodeFormComponent implements OnDestroy {

  selectedNodeType!: string;
  node!: Node;
  subscription: Subscription = new Subscription();
  formMapper: any = {
    'deployment': DeploymentFormComponent,
    'configMap': ConfigMapFormComponent,
    'pod': PodFormComponent
    //TODO add more forms here
  };

  componentRef!: ComponentRef<any>;

  constructor(
    private diagramService: DiagramService,
    private store: Store,
    public viewContainerRef: ViewContainerRef
  ) { }

  ngOnInit(): void {


    this.subscription.add(this.diagramService.selectedNode$.pipe(
      filter((node: go.Node) => node !== null && node !== undefined),
      tap((node: go.Node) => {
        this.selectedNodeType = node?.data?.type || null
        this.viewContainerRef.clear();
        this.componentRef = this.viewContainerRef.createComponent(this.formMapper[this.selectedNodeType]);
      }),
      switchMap((node: go.Node) => {
        const nodeId = node?.data?.key;
        if (nodeId) {
          return this.store.select(getNodeById(nodeId));
        } else {
          return of(null);
        }
      }),
      filter((node): node is Node => node !== null && node !== undefined)
    ).subscribe((node: Node) => {
      this.node = node;
      this.componentRef.instance.selectedNode = node;
    }));
  }

  ngOnDestroy(): void {
    this.componentRef.destroy();
    this.subscription.unsubscribe();
  }

}
