/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
