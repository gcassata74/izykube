import { Node } from './../../model/node.class';
import { Store } from '@ngrx/store';
import { Component, OnDestroy, ViewContainerRef, ComponentRef, ViewChild } from '@angular/core';
import { switchMap, of, filter, tap, Subscription, mergeMap } from 'rxjs';
import { DiagramService } from 'src/app/services/diagram.service';
import { getNodeById } from 'src/app/store/selectors/selectors';
import { DeploymentFormComponent } from '../deployment-form/deployment-form.component';
import { ConfigMapFormComponent } from '../config-map-form/config-map-form.component';
import { PodFormComponent } from '../pod-form/pod-form.component';


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
    'configMap': ConfigMapFormComponent,
    'pod': PodFormComponent
    //TODO add more forms here
  };

  componentRef!: ComponentRef<any>;

  constructor(
    private diagramService: DiagramService,
    private store: Store,
  ) { }

  ngOnInit(): void {


    this.subscription.add(this.diagramService.selectedNode$.pipe(
      filter((node: go.Node) => node !== null && node !== undefined),
      tap((node: go.Node) => {
        this.selectedNodeType = node?.data?.type || null
        this.dynamicContainer.clear();
        if (this.formMapper[this.selectedNodeType]) {
          this.componentRef = this.dynamicContainer.createComponent(this.formMapper[this.selectedNodeType]);
        }
      }),
      mergeMap((node: go.Node) => {
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
    this.componentRef?.destroy();
    this.subscription.unsubscribe();
  }

}
