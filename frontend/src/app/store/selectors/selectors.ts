import { createFeatureSelector, createSelector } from "@ngrx/store";
import { ClusterState, MainState } from "../states/state";
import { Cluster } from "src/app/model/cluster.class";

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
  (clusterData: Cluster) => {
    return clusterData?.nodes.find(node => node.id === nodeId);
  });

export const selectClusterDiagram = createSelector(
  getClusterData,
  (clusterData: Cluster) => clusterData?.diagram
);

export const getHasTemplate = createSelector(
   getClusterData,
    (clusterData: Cluster) => clusterData.hasTemplate
);

export const getIsDeployed = createSelector(
    getClusterData,
     (clusterData: Cluster) => clusterData.isDeployed
);



