
//reducer.ts
import { createReducer, on } from '@ngrx/store';
import * as actions from '../actions/actions';
import { AppState, initialState } from '../states/state';
import { Cluster } from 'src/app/model/cluster.class';

export const reducer = createReducer(
  initialState.mainState,

  on(actions.setCurrentAction, (state: AppState, { action }) => {
    return {
      ...state,
      currentAction: action,
    }
  }),

  on(actions.resetCurrentAction, (state: AppState) => {
    return {
      ...state,
      currentAction: null,
    }
  })
);

