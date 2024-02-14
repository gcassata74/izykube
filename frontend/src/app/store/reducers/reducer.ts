
//reducer.ts
import { createReducer, on } from '@ngrx/store';
import * as actions from '../actions/actions';
import { MainState, initialState } from '../states/state';


export const reducer = createReducer(
  initialState.mainState,

  on(actions.setCurrentAction, (state: MainState, { action }) => {
    return {
    ...state,
    currentAction: action,
    }
  }),

  on(actions.resetCurrentAction, (state: MainState) => {
    return {
    ...state,
    currentAction: null,
  }
  })


);