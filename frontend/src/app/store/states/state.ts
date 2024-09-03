import { createReducer, on } from '@ngrx/store';
import * as actions from '../actions/actions';
import { Cluster } from '../../model/cluster.class';
import { ClusterStatusEnum } from 'src/app/cluster/enum/cluster.-status-enum';


export interface AppState {
 currentAction: string | null;
}

export interface ClusterState {
  clusters: Cluster[];
  currentCluster: Cluster;
 }

export interface State {
  mainState: AppState
  clusterState: ClusterState
}

// Create an instance of Cluster for the initial state
const initialCluster = new Cluster(
  null,
  'Cluster1',
  [],
  [], 
  '', 
  'default',  
  ClusterStatusEnum.CREATED 
);

export const initialState: State = {
  mainState: {
    currentAction: null,
  },
  clusterState: {
    clusters: [],
    currentCluster: initialCluster
  }
}
