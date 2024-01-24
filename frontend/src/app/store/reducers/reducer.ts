import { Action, createReducer, on } from "@ngrx/store";
import { MainState, initialState } from "../states/state";
import * as actions from '../actions/action';

const featureReducer = createReducer(
    initialState.mainState,
  
    on(actions.Init, (state: MainState) => ({
      ...state,
    })),
  
  );
  
  export function reducer(
    state: MainState | undefined,
    action: Action,
  ): MainState {
    return featureReducer(state, action);
  }
  