import { createReducer, on } from '@ngrx/store';
import * as actions from '../actions/actions';
import { AppState, initialState } from '../states/state';
import { Cluster } from 'src/app/model/cluster.class';

export const clusterReducer = createReducer(
    initialState.clusterState,
  
  
    on(actions.addNode, (state, { node }) => ({
        ...state,
        currentCluster: {
          ...state.currentCluster,
          nodes: [...state.currentCluster.nodes, node]
        }
    
      })),
  
    on(actions.removeNode, (state, { nodeId }) => ({
      ...state,
      currentCluster: {
        ...state.currentCluster,
        nodes: state.currentCluster.nodes.filter((node: { id: string; }) => node.id !== nodeId),
      },
    })),
  
    on(actions.updateNode, (state, { nodeId, formValues }) => ({
      ...state,
      currentCluster: {
        ...state.currentCluster,
        nodes: state.currentCluster.nodes.map((node: { id: string; }) =>
          node.id === nodeId ? { ...node, ...formValues } : node
        )
      }
    })),
  
    on(actions.addLink, (state, { link }) => ({
      ...state,
      currentCluster: {
        ...state.currentCluster,
        links: [...state.currentCluster.links, link]
      }
    })),
  
    on(actions.removeLink, (state, { source, target }) => ({
      ...state,
      currentCluster: {
        ...state.currentCluster,
        links: state.currentCluster.links.filter((link: { source: string; target: string; }) => link.source !== source || link.target !== target)
      }
    })),
  
  
    on(actions.updateDiagram, (state, { diagramData }) => ({
      ...state,
      currentCluster: {
        ...state.currentCluster,
        diagram: diagramData
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
      return {
        ...state,
        currentCluster: Cluster.fromJSON(cluster)
      }
    }),
  
    on(actions.loadClusters, (state, { clusters }) => ({
        ...state,
        clusters: clusters.map((cluster: any) => Cluster.fromJSON(cluster))
      })),
  
  );