import { createFeatureSelector, createSelector } from "@ngrx/store";
import { MainState } from "../states/state";

export const getMainState = createFeatureSelector<MainState>(
    'mainState',
  );
  

  export const getCurrentAction = createSelector(
    getMainState,
    (state: MainState) => state?.currentAction
  );  