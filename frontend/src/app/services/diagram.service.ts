import { Injectable, OnDestroy } from '@angular/core';
import * as go from 'gojs';
import { BehaviorSubject, Subject, tap, Subscription, catchError, of } from 'rxjs';
import { addLink, addNode, removeLink, removeNode } from '../store/actions/cluster.actions';
import { Store } from '@ngrx/store';
import { PodSpec } from '../model/pod.interface';
import { Container } from '../model/container.interface';
import { Service } from '../model/service.interface';
import { Volume } from '../model/volume.interface';
import { Ingress } from '../model/ingress.interface';
import { ConfigMap } from '../model/config-map.interface';
import { Link } from '../model/link.interface';
import { Deployment } from '../model/deployment.interface';
import { getClusterData } from '../store/selectors/selectors';
import { Cluster } from '../model/cluster.interface';
import { DataService } from './data.service';
import { NodeTemplateFunctions, nodeTemplates } from '../config/node-templates.config';
import { Base } from '../model/base.interface';


@Injectable({
  providedIn: 'root'
})
export class DiagramService implements OnDestroy{


  private subscription = new Subscription();
  private _selectedNode = new BehaviorSubject<go.Node | null>(null);
  readonly selectedNode$ = this._selectedNode.asObservable();


  constructor(
    private store: Store,
    private dataservice: DataService
  ) { }


  createNode<T extends Base>(type: new () => T, id: string, name: string): T {
    const node = new type();
    node.id = id;
    node.name = name;
    return node;
  }

  onSelectionChanged(e: go.DiagramEvent): void {
    const selectedNode = e.diagram.selection.first();
    if (selectedNode instanceof go.Node) {
      this._selectedNode.next(selectedNode);
    }
  }

  onNodeDropped(e: go.DiagramEvent): void {
    const droppedNode = e.subject.first();

    if (droppedNode instanceof go.Node) {
      const type = droppedNode.data.type as keyof NodeTemplateFunctions;

      const templateFn = nodeTemplates[type];

      if (templateFn) {
        let newNodeData = templateFn();
        newNodeData.id = droppedNode.data.key;
        newNodeData.name = droppedNode.data.name;
        this.store.dispatch(addNode({ node: newNodeData }));
      } else {
        console.warn(`Unhandled node type: ${type}`);
      }
    }
  }



  onNodeDeleted(e: go.DiagramEvent): void {
    const selectedNode = e.diagram.selection.first();
    if (selectedNode instanceof go.Node) {
      if (!selectedNode?.data?.type) return;
      this.store.dispatch(removeNode({ nodeId: selectedNode.data.key }));
    } else if (selectedNode instanceof go.Link) {
      this.store.dispatch(removeLink({ source: selectedNode.data.from, target: selectedNode.data.to }));
    }
  }

  onLinkDrawn(e: go.DiagramEvent): void {
    const link = e.subject;
    if (link instanceof go.Link) {
      let newLink: Link;
      newLink = {
        source: link.data.from,
        target: link.data.to
      };
      this.store.dispatch(addLink({ link: newLink }));
    }
  }


  saveDiagram() {
    this.subscription.add(
      this.store.select(getClusterData).pipe(
        tap((clusterData) => {
          this.dataservice.post<Cluster>('cluster', clusterData).pipe(
            catchError((error) => { console.error('Error saving diagram', error); return of(error); })
          ).subscribe(() => {
            alert('Diagram saved successfully')
          });
        })
      ).subscribe()
    );
  }

  ngOnDestroy(): void {
   this.subscription.unsubscribe();
  }


}


