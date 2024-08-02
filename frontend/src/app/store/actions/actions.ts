import { createAction, props } from '@ngrx/store';
import { Link } from '../../model/link.class';
import { Node } from '../../model/node.class';
import { Cluster } from 'src/app/model/cluster.class';

//toolbar actions
export const INIT = '@ngrx/store/init';
export const SET_CURRENT_ACTION = '[Toolbar] Set Current Action';
export const RESET_CURRENT_ACTION = '[Toolbar] Reset Current Action';

//cluster actions
export const ADD_NODE = '[Cluster] Add Node';
export const REMOVE_NODE = '[Cluster] Remove Node';
export const ADD_LINK = '[Cluster] Add Link';
export const REMOVE_LINK = '[Cluster] Remove Link';
export const UPDATE_DIAGRAM = '[Cluster] Update Diagram';
export const UPDATE_NODE = '[Cluster] Update Node';
export const UPDATE_CLUSTER = '[Cluster] Update Cluster';
export const LOAD_CLUSTER = '[Cluster] Load Cluster Request';
export const LOAD_CLUSTER_SUCCESS = '[Cluster] Load Cluster Success';
export const LOAD_CLUSTER_ERROR = '[Cluster] Load Cluster Error';
export const CREATE_TEMPLATE = '[Cluster] Create Template';
export const DELETE_TEMPLATE = '[Cluster] Delete Template';
export const DEPLOY = '[Cluster] Deploy';
export const UNDEPLOY = '[Cluster] Undeploy';


//toolbar actions
export const Init = createAction(INIT);

export const setCurrentAction = createAction(
    SET_CURRENT_ACTION,
    props<{ action: string }>()
  );

  export const resetCurrentAction = createAction(
    RESET_CURRENT_ACTION
  );  


//cluster actions
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

export const updateCluster = createAction(
  UPDATE_CLUSTER,
  props<{ cluster: Cluster }>()
);

// this replaces current cluster in the store with the new one
export const loadCluster = createAction(
  LOAD_CLUSTER,
  props<{ cluster: Cluster }>()
);







