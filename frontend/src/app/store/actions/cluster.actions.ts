import { createAction, props } from '@ngrx/store';
import { ClusterNode } from '../../model/node.class'; // Import your node model


export const ADD_NODE = '[Cluster] Add Node';
export const REMOVE_NODE = '[Cluster] Remove Node';
export const UPDATE_DIAGRAM = '[Cluster] Update Diagram';

export const addNode = createAction(
  ADD_NODE,
  props<{ node: ClusterNode }>()
);

export const removeNode = createAction(
  REMOVE_NODE,
  props<{ nodeId: string }>()
);

export const updateDiagram = createAction(
  UPDATE_DIAGRAM,
  props<{ diagramData: string }>()
);