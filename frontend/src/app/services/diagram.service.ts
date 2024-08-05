import { Injectable, OnDestroy } from '@angular/core';
import * as go from 'gojs';
import { BehaviorSubject, Subject, tap, Subscription, catchError, of, throwError, Observable } from 'rxjs';
import { addLink, addNode, removeLink, removeNode, updateCluster, updateNode } from '../store/actions/actions';
import { Store } from '@ngrx/store';

import { Link } from '../model/link.class';
import { getCurrentCluster } from '../store/selectors/selectors';
import { Cluster } from '../model/cluster.class';
import { DataService } from './data.service';
import { NodeFactoryService } from './node.factory.service';
import { FormGroup } from '@angular/forms';
import { Node } from '../model/node.class';


@Injectable({
  providedIn: 'root'
})
export class DiagramService implements OnDestroy{

  private subscription = new Subscription();
  private _selectedNode = new Subject<go.Node>();
  readonly selectedNode$ = this._selectedNode.asObservable();

  constructor(
    private store: Store,
    private dataservice: DataService,
    private nodeFactory: NodeFactoryService
  ) { }

  onSelectionChanged(e: go.DiagramEvent): void {
    const selectedNode = e.diagram.selection.first();
    if (selectedNode instanceof go.Node) {
      this._selectedNode.next(selectedNode);
    }
  }

  onNodeDropped(e: go.DiagramEvent): void {

    const droppedNode = e.subject.first();
    const type = droppedNode.data.type;
    const name = droppedNode.data.name;
    const id = droppedNode.data.key;

    if (droppedNode instanceof go.Node) {
        const newNode = this.nodeFactory.createNode(type, id, name);
        this.store.dispatch(addNode({ node: newNode }));
      } else {
        console.warn(`Unhandled node type: ${type}`);
      }
  }

  onNodeEdited(e: go.DiagramEvent): void {

    const textBlock = e.subject;
    const node = textBlock.part;
    if (node instanceof go.Node && node.data) {
      const newName = textBlock.text;
      const nodeId = node.data.key;
      this.store.dispatch(updateNode({ nodeId, formValues: {name: newName} }));
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
      let newLink: Link = new Link(link.data.from, link.data.to);
      this.store.dispatch(addLink({ link: newLink }));
    }
  }

  // saveDiagram(clusterData: Cluster) {
  //   let saveObservable: Observable<Cluster>;
  
  //   if (clusterData.id) {
  //     // If the ID is present, use PUT to update
  //     saveObservable = this.dataservice.put<Cluster>('cluster/' + clusterData.id, clusterData);
  //   } else {
  //     // If the ID is not present, use POST to create
  //     saveObservable = this.dataservice.post<Cluster>('cluster', clusterData);
  //   }
  
  //   saveObservable.pipe(
  //     catchError((error) => {
  //       console.error('Error saving diagram', error);
  //       return throwError(() => error);
  //     })
  //   ).subscribe((savedCluster:Cluster) => {
  //     // Dispatch the update action with the saved cluster data
  //     this.store.dispatch(updateCluster({ cluster: savedCluster }));
  //   });
  // }
  

  ngOnDestroy(): void {
   this.subscription.unsubscribe();
  }

  updateClusterNodes(nodeId: string, formValues: any) {
   this.store.dispatch(updateNode({ nodeId, formValues }));
  }

}


