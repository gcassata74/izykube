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

// Selector to get a node by ID from within the cluster data
export const getNodeById = (nodeId: string) => createSelector(
  getClusterData,
  (clusterData) => {
    return clusterData?.nodes.find(node => node.id === nodeId);
  });




