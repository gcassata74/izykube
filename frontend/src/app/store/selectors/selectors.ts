/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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

export const getSelectedNodeId = createSelector(
  getActionState,
  (state: ActionState) => state?.currentAction
);

export const getLinkById = (linkId: string) => createSelector(
  getCurrentCluster,
  (currentCluster: Cluster) => {
    return (currentCluster?.links || []).find((link: any) => link.id === linkId);
  });
