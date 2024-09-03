import { ActionReducerMap } from '@ngrx/store';
import { State } from '../states/state';
import { reducer } from './reducer';
import { clusterReducer } from './cluster.reducer';


export const reducers: ActionReducerMap<State> = {
  mainState: reducer,
  clusterState: clusterReducer
};
