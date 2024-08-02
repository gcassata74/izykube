import { ActionReducerMap } from '@ngrx/store';
import { State } from '../states/state';
import { reducer,clusterReducer } from './reducer';


export const reducers: ActionReducerMap<State> = {
  mainState: reducer,
  clusterState: clusterReducer
};
