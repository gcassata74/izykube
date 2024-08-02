import { clusterReducer } from './../reducers/reducer';
import { createReducer, on } from '@ngrx/store';
import * as actions from '../actions/actions';
import { Cluster } from '../../model/cluster.class';


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

export const initialState: State = {
  mainState: {
    currentAction: null,

  },
  clusterState: {
    clusters: [],
    currentCluster: {
      name: 'Cluster1',
      nodes: [],
      links:[],
      diagram: '',
      nameSpace: 'default',
      id: null,
      isDeployed: false,
      hasTemplate: false
    }
  }
}

