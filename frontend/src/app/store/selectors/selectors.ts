import { createFeatureSelector, createSelector, State } from "@ngrx/store";
import { ClusterState, AppState } from "../states/state";
import { Cluster } from "src/app/model/cluster.class";

export const getMainState = createFeatureSelector<AppState>(
  'mainState'
);

export const getClusterState = createFeatureSelector<ClusterState>(
  'clusterState'
);

export const getCurrentAction = createSelector(
  getMainState,
  (state: AppState) => state?.currentAction
);

export const getCurrentCluster = createSelector(
  getClusterState,
  (state: ClusterState) => state?.currentCluster
);

// Selector to get a node by ID from within the current cluster
export const getNodeById = (nodeId: string) => createSelector(
  getCurrentCluster,
  (currentCluster: Cluster) => {
    return currentCluster?.nodes.find(node => node.id === nodeId);
  });

export const selectClusterDiagram = createSelector(
  getCurrentCluster,
  (currentCluster: Cluster) => currentCluster?.diagram
);

export const getStatus = createSelector(
   getCurrentCluster,
    (currentCluster: Cluster) => currentCluster.status
);



export const getClusters = createSelector(
  getClusterState,
  (state) => state.clusters
);


