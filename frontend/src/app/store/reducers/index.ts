import { ActionReducerMap } from '@ngrx/store';
import { State } from '../states/state';
import { actionReducer, clusterReducer } from './reducer';

export const reducers: ActionReducerMap<State> = {
  actionState: actionReducer,
  clusterState: clusterReducer
};
