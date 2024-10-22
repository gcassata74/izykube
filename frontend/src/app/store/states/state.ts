import { createReducer, on } from '@ngrx/store';
import * as actions from '../actions/actions';
import { Cluster } from '../../model/cluster.class';
import { ClusterStatusEnum } from 'src/app/cluster/enum/cluster.-status-enum';


export interface ActionState {
 currentAction: string | null;
}

export interface ClusterState {
  clusters: Cluster[];
  currentCluster: Cluster;
 }

export interface State {
  actionState: ActionState;
  clusterState: ClusterState;
}

export const initialState: State = {
  actionState: {
    currentAction: null
  },
  clusterState: {
    clusters: [],
    currentCluster: new Cluster()
  },
}
