import { clusterReducer } from './../reducers/reducer';
import { createReducer, on } from '@ngrx/store';
import * as actions from '../actions/actions';
import { Cluster } from '../../model/cluster.class';


export interface MainState {
 currentAction: string | null;
 diagramModel: go.Model | null;
}

export interface ClusterState {
   clusterData: Cluster
 }

export interface State {
  mainState: MainState
  clusterState: ClusterState
}

export const initialState: State = {
  mainState: {
    currentAction: null,
    diagramModel: null,
  },
  clusterState: {
    clusterData: {
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

