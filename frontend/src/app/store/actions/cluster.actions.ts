import { createAction, props } from '@ngrx/store';
import { Link } from '../../model/link.class';
import { Node } from '../../model/node.class';


export const ADD_NODE = '[Cluster] Add Node';
export const REMOVE_NODE = '[Cluster] Remove Node';
export const ADD_LINK = '[Cluster] Add Link';
export const REMOVE_LINK = '[Cluster] Remove Link';
export const UPDATE_DIAGRAM = '[Cluster] Update Diagram';
export const UPDATE_NODE = '[Cluster] Update Node';

export const addNode = createAction(
  ADD_NODE,
  props<{ node: Node }>()
);

export const removeNode = createAction(
  REMOVE_NODE,
  props<{ nodeId: string }>()
);

export const addLink = createAction(
  ADD_LINK,
  props<{ link: Link }>()
);

export const removeLink = createAction(
  REMOVE_LINK,
  props<{ source: string, target:string }>()
);

export const updateDiagram = createAction(
  UPDATE_DIAGRAM,
  props<{ diagramData: string }>()
);

export const updateNode = createAction(
  UPDATE_NODE,
  props<{ nodeId: string, formValues: any }>()
);


