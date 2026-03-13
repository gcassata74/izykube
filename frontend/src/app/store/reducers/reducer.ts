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


//reducer.ts
import { createReducer, on } from '@ngrx/store';
import * as actions from '../actions/actions';
import { Cluster } from 'src/app/model/cluster.class';
import { ActionState, initialState } from '../states/state';

export const actionReducer = createReducer(
  initialState.actionState,

  on(actions.setCurrentAction, (state: ActionState, { action }) => {
    return {
      ...state,
      currentAction: action,
    }
  }),

  on(actions.resetCurrentAction, (state: ActionState) => {
    return {
      ...state,
      currentAction: null,
    }
  })
);


export const clusterReducer = createReducer(
  initialState.clusterState,


  on(actions.addNode, (state, { node }) => { 
    const currentNodes = state.currentCluster.nodes || [];
    const updated_state:any = {
      ...state,
      currentCluster: {
        ...state.currentCluster,
        nodes: [...currentNodes, node]
      }
    }
  
    return updated_state;
  
  }),

  on(actions.removeNode, (state, { nodeId }) => ({
    ...state,
    currentCluster: {
      ...state.currentCluster,
      nodes: (state.currentCluster.nodes || []).filter((node: { id: string; }) => node.id !== nodeId),
    },
  })),

  on(actions.updateNode, (state, { nodeId, formValues }) => ({
    ...state,
    currentCluster: {
      ...state.currentCluster,
      nodes: (state.currentCluster.nodes || []).map((node: { id: string; }) =>
        node.id === nodeId ? { ...node, ...formValues } : node
      )
    }
  })),

  on(actions.addLink, (state, { link }) => ({
    ...state,
    currentCluster: {
      ...state.currentCluster,
      links: [...(state.currentCluster.links || []), link]
    }
  })),

  on(actions.removeLink, (state, { source, target }) => ({
    ...state,
    currentCluster: {
      ...state.currentCluster,
      links: (state.currentCluster.links || []).filter((link: { source: string; target: string; }) => link.source !== source || link.target !== target)
    }
  })),

  on(actions.updateLink, (state, { linkId, changes }) => ({
    ...state,
    currentCluster: {
      ...state.currentCluster,
      links: (state.currentCluster.links || []).map((link: any) => {
        const matches = link.id === linkId || (!link.id && `${link.source}->${link.target}` === linkId);
        if (!matches) {
          return link;
        }
        const nextType = changes.type === 'Use'
          ? 'Use'
          : changes.type === 'Container'
            ? 'Container'
            : changes.type === 'Expose'
              ? 'Expose'
              : changes.type === 'serviceAccountBinding'
                ? 'serviceAccountBinding'
                : changes.type === 'appliesTo'
                  ? 'appliesTo'
                : (link.type || 'Expose');
        const nextNote = 'note' in changes ? changes.note : link.note;
        const next: any = {
          ...link,
          ...changes,
          type: nextType,
          ...(nextNote !== undefined ? { note: nextNote } : {})
        };
        if ((changes as any).clearContainerRole || next.type !== 'Container') {
          delete next.containerRole;
        }
        delete next.clearContainerRole;
        return next;
      })
    }
  })),


  on(actions.updateDiagram, (state, { diagramData, links }) => ({
    ...state,
    currentCluster: {
      ...state.currentCluster,
      diagram: diagramData,
      links: links || []
    }
  })),

  on(actions.updateCluster, (state, { cluster }) => {
    return {
      ...state,
      currentCluster: {
        ...state.currentCluster,
        ...cluster
      }
    }
  }),

  on(actions.loadCluster, (state, { cluster }) => {

    const updated_state ={
      ...state,
      currentCluster: Cluster.fromJSON(cluster)
    }

    return updated_state
  }),


  on(actions.loadClusters, (state, { clusters }) => ({
      ...state,
      clusters: clusters.map((cluster: any) => Cluster.fromJSON(cluster))
    })),

);
