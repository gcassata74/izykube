import { createFeatureSelector, createSelector, State } from "@ngrx/store";
import { ClusterState, ActionState } from "../states/state";
import { Cluster } from "src/app/model/cluster.class";

export const getActionState = createFeatureSelector<ActionState>(
  'actionState'
);

export const getClusterState = createFeatureSelector<ClusterState>(
  'clusterState'
);

export const getCurrentAction = createSelector(
  getActionState,
  (state: ActionState) => state?.currentAction
);

export const getCurrentCluster = createSelector(
  getClusterState,
  (state: ClusterState) => state?.currentCluster
);

export const getNodeById = (nodeId: string) => createSelector(
  getCurrentCluster,
  (currentCluster: Cluster) => {
    return currentCluster.nodes.find(node => node.id === nodeId);
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


