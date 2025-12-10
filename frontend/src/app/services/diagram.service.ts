import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Store } from '@ngrx/store';
import { addNode, removeNode, selectNode, updateNode } from '../store/actions/actions';
import { NodeFactoryService } from './node.factory.service';


@Injectable({
  providedIn: 'root'
})
export class DiagramService {
  private _selectedNodeId = new BehaviorSubject<string | null>(null);
  readonly selectedNodeId$ = this._selectedNodeId.asObservable();
  private _selectedLinkId = new BehaviorSubject<string | null>(null);
  readonly selectedLinkId$ = this._selectedLinkId.asObservable();

  constructor(
    private store: Store,
    private nodeFactory: NodeFactoryService,
  ) { }

  updateClusterNodes(nodeId: string, formValues: any) {
    const sanitized = this.sanitizeFormValues(formValues);
    this.store.dispatch(updateNode({ nodeId, formValues: sanitized }));
  }

  addClusterNode(type: string, nodeId: string, name: string): void {
    const node = this.nodeFactory.createNode(type, nodeId, name);
    this.store.dispatch(addNode({ node }));
  }

  removeClusterNode(nodeId: string): void {
    this.store.dispatch(removeNode({ nodeId }));
  }

  setSelectedNode(nodeId: string | null): void {
    const currentValue = this._selectedNodeId.getValue();
    if (currentValue === nodeId) {
      return;
    }

    this._selectedNodeId.next(nodeId);
    if (nodeId) {
      this.clearSelectedLink();
    }

    if (nodeId) {
      this.store.dispatch(selectNode({ nodeId }));
    }
  }

  clearSelectedNode(): void {
    this.setSelectedNode(null);
  }

  setSelectedLink(linkId: string | null): void {
    if (this._selectedLinkId.getValue() === linkId) {
      return;
    }
    if (linkId) {
      this.clearSelectedNode();
    }
    this._selectedLinkId.next(linkId);
  }

  clearSelectedLink(): void {
    this._selectedLinkId.next(null);
  }

  private sanitizeFormValues(values: any): any {
    if (!values || typeof values !== 'object') {
      return values;
    }
    const sanitized = { ...values };
    if (typeof sanitized.name === 'string') {
      sanitized.name = sanitized.name.trim();
    }
    return sanitized;
  }
}
