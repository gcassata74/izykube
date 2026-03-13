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
export const UPDATE_LINK = '[Cluster] Update Link';
export const UPDATE_DIAGRAM = '[Cluster] Update Diagram';
export const UPDATE_NODE = '[Cluster] Update Node';
export const UPDATE_CLUSTER = '[Cluster] Update Cluster';
export const LOAD_CLUSTER = '[Cluster] Load Cluster Request';
export const LOAD_CLUSTERS = '[Cluster] Load Clusters';
export const LOAD_CLUSTER_SUCCESS = '[Cluster] Load Cluster Success';
export const LOAD_CLUSTER_ERROR = '[Cluster] Load Cluster Error';
export const CREATE_TEMPLATE = '[Cluster] Create Template';
export const DELETE_TEMPLATE = '[Cluster] Delete Template';
export const DEPLOY = '[Cluster] Deploy';
export const UNDEPLOY = '[Cluster] Undeploy';
export const SELECT_NODE = '[Cluster] Select Node';


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

export const updateLink = createAction(
  UPDATE_LINK,
  props<{ linkId: string, changes: Partial<Link> & { clearContainerRole?: boolean } }>()
);

export const updateDiagram = createAction(
  UPDATE_DIAGRAM,
  props<{ diagramData: string, links: Link[] }>()
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

//when all the clusters are loaded
export const loadClusters = createAction(
  LOAD_CLUSTERS,
  props<{ clusters: Cluster[] }>()
);

export const selectNode = createAction(
  SELECT_NODE,
  props<{ nodeId: string }>()
);
