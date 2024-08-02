
//reducer.ts
import { createReducer, on } from '@ngrx/store';
import * as actions from '../actions/actions';
import { MainState, initialState } from '../states/state';
import { addNode } from '../actions/actions';


export const reducer = createReducer(
  initialState.mainState,

  on(actions.setCurrentAction, (state: MainState, { action }) => {
    return {
    ...state,
    currentAction: action,
    }
  }),

  on(actions.resetCurrentAction, (state: MainState) => {
    return {
    ...state,
    currentAction: null,
  }
  })


);

export const clusterReducer = createReducer(
  initialState.clusterState,


  on(addNode, (state, { node }) => ({
      ...state,
      clusterData: {
        ...state.clusterData,
        nodes: [...state.clusterData.nodes, node]
      }

  })),

  on(actions.removeNode, (state, { nodeId }) => ({
      ...state,
      clusterData: {
        ...state.clusterData,
        nodes: state.clusterData.nodes.filter((node: { id: string; }) => node.id !== nodeId),
      },
  })),

  on(actions.updateNode, (state, { nodeId, formValues }) => ({
      ...state,
      clusterData: {
        ...state.clusterData,
        nodes: state.clusterData.nodes.map((node: { id: string; }) =>
          node.id === nodeId ? { ...node, ...formValues } : node
        )
      }
  })),

  on(actions.addLink, (state, { link }) => ({
    ...state,
    clusterData: {
      ...state.clusterData,
      links: [...state.clusterData.links, link]
    }
})),

  on(actions.removeLink, (state, { source, target }) => ({
    ...state,
    clusterData: {
      ...state.clusterData,
      links: state.clusterData.links.filter((link: { source: string; target: string; }) => link.source !== source || link.target !== target)
    }
  })),


  on(actions.updateDiagram, (state, { diagramData }) => ({
    ...state,
    clusterData: {
      ...state.clusterData,
      diagram: diagramData
      }
  })),

  on(actions.updateCluster, (state, { cluster }) => ({
    ...state,
    clusterData: {
      ...state.clusterData,
      ...cluster // Integra i nuovi dati dell'oggetto 'cluster' con quelli esistenti in 'clusterData'
    }
  })),

  on(actions.loadCluster, (state, { cluster }) => ({
    ...state,
    clusterData: cluster
  }))

 

);