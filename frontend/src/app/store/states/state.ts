import { createReducer, on } from '@ngrx/store';
import * as actions from '../actions/actions';


export interface MainState {
 currentAction: string | null;
}

export interface State {
  mainState: MainState;
}

export const initialState: State = {
  mainState: {
    currentAction: null
  }
}

