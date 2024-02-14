import { Injectable } from '@angular/core';
import * as go from 'gojs';
import { BehaviorSubject, Subject } from 'rxjs';
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

type NodeTemplateFunctions = {
  pod: () => PodSpec,
  container: () => Container,
  ingress: () => Ingress,
  service: () => Service,
  configMap: () => ConfigMap,
  deployment: () => Deployment,
  volume: () => Volume
};

@Injectable({
  providedIn: 'root'
})
export class DiagramService {


  private _selectedNode = new BehaviorSubject<go.Node | null>(null);
  readonly selectedNode$ = this._selectedNode.asObservable();

  
  nodeTemplates: NodeTemplateFunctions = {
    pod: () => ({ id: '', name: '', containers: [], volumes: [] }),
    container: () => ({ id: '', name: '', image: '', ports: [] }),
    ingress: () => ({ id: '', name: '', rules: [] }),
    service: () => ({ id: '', name: '', type: 'ClusterIP', ports: [{ port: 80, targetPort: 80, protocol: 'TCP' }], selector: {} }),
    configMap: () => ({ id: '', name: '', data: {} }),
    deployment: () => ({ id: '', name: '', replicas: 1, selector: { matchLabels: {} }, template: { metadata: { labels: {} }, spec: {
      containers: [],  id: '', name: '' } }, containers: [] }),
    volume: () => ({ id: '', name: '', persistentVolumeClaim: { claimName: '' } })
  };

 
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

    if (droppedNode instanceof go.Node) {
      const type = droppedNode.data.type as keyof NodeTemplateFunctions;

      const templateFn = this.nodeTemplates[type];

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


}


