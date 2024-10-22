
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
    
    console.log("state", node)

    const updated_state:any = {
      ...state,
      currentCluster: {
        ...state.currentCluster,
        nodes: [...state.currentCluster.nodes, node]
      }
    }
  
    return updated_state;
  
  }),

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

