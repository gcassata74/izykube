import { Injectable } from '@angular/core';
import * as go from 'gojs';
import { BehaviorSubject, Subject } from 'rxjs';
import { ConfigMap, Container, Deployment, Ingress, Pod, Service } from '../model/node.class';
import { addNode, removeNode } from '../store/actions/cluster.actions';
import { Store } from '@ngrx/store';

@Injectable({
  providedIn: 'root'
})
export class DiagramService {


  private _selectedNode = new BehaviorSubject<go.Node | null>(null);
  readonly selectedNode$ = this._selectedNode.asObservable();

  constructor(
    private store:Store,
  ) { }

  onSelectionChanged(e: go.DiagramEvent): void {
    const selectedNode = e.diagram.selection.first();
    if (selectedNode instanceof go.Node) {
    this._selectedNode.next(selectedNode);
    }
  }

  onNodeDropped(e: go.DiagramEvent): void {
    const droppedNode = e.subject.first();

    // Check if the dropped part is actually a node
    if (droppedNode instanceof go.Node) {
      if (!droppedNode?.data?.type) return; // Exit if no type is defined
      
      let newNode;
      switch (droppedNode.data.type) {
          case 'pod':
              newNode = new Pod(droppedNode);
              break;
          case 'container':
              newNode = new Container(droppedNode);
              break;
          case 'ingress':
              newNode = new Ingress(droppedNode);
              break;
          case 'service':
              newNode = new Service(droppedNode);
              break;
          case 'deployment':
              newNode = new Deployment(droppedNode);
              break;
          case 'configMap':
              newNode = new ConfigMap(droppedNode);    
              break;    
          default:
              console.warn(`Unhandled node type: ${droppedNode.data.type}`);
              return; 
      }
      if (newNode) {
        this.store.dispatch(addNode({ node: newNode }));
      }
    }
  }

  onNodeDeleted(e: go.DiagramEvent): void {
    const selectedNode = e.diagram.selection.first();
    if (selectedNode instanceof go.Node) {
      if (!selectedNode?.data?.type) return; 
      this.store.dispatch(removeNode({ nodeId: selectedNode.data.key }));
    }
  }
}


