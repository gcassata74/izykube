import { createFeatureSelector, createSelector } from "@ngrx/store";
import { ClusterState, MainState } from "../states/state";

export const getMainState = createFeatureSelector<MainState>(
    'mainState'
  );

  export const getClusterState = createFeatureSelector<ClusterState>(
    'clusterState'
  );

export const getCurrentAction = createSelector(
    getMainState,
    (state: MainState) => state?.currentAction
  );  

export const getClusterData = createSelector(
    getClusterState,
    (state: ClusterState) => state?.clusterData
  );  




