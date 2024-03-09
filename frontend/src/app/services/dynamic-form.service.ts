import { NodeFactoryService } from './node.factory.service';
import { Injectable } from '@angular/core';
import { DiagramService } from './diagram.service';
import { Store } from '@ngrx/store';
import { Observable, filter, mergeMap, of } from 'rxjs';
import { getNodeById } from '../store/selectors/selectors';
import { Node } from '../model/node.class';

@Injectable({
  providedIn: 'root'
})
export class DynamicFormService {

  constructor(private diagramService: DiagramService, private store: Store) {}

  getNode(nodeId: string): Observable<Node> {
      return this.store.select(getNodeById(nodeId)).pipe
      (filter((node): node is Node => node !== null && node !== undefined));
  }

 
}
