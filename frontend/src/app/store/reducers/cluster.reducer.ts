// cluster.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { addLink, addNode, removeLink, removeNode, updateDiagram, updateNode } from '../actions/cluster.actions';
import { initialState } from '../states/state';


export const clusterReducer = createReducer(
  initialState.clusterState,


  on(addNode, (state, { node }) => ({
      ...state,
      clusterData: {
        ...state.clusterData,
        nodes: [...state.clusterData.nodes, node]
      }
    
  })),

  on(removeNode, (state, { nodeId }) => ({
      ...state,
      clusterData: {
        ...state.clusterData,
        nodes: state.clusterData.nodes.filter((node: { id: string; }) => node.id !== nodeId),
      },
  })),

  on(updateNode, (state, { nodeId, formValues }) => ({
      ...state,
      clusterData: {
        ...state.clusterData,
        nodes: state.clusterData.nodes.map(node =>
          node.id === nodeId ? { ...node, ...formValues } : node
        )
      }
  })),

  on(addLink, (state, { link }) => ({
    ...state,
    clusterData: {
      ...state.clusterData,
      links: [...state.clusterData.links, link]
    }
})),

  on(removeLink, (state, { source, target }) => ({
    ...state,
    clusterData: {
      ...state.clusterData,
      links: state.clusterData.links.filter(link => link.source !== source || link.target !== target)
    }
  })),


  on(updateDiagram, (state, { diagramData }) => ({
    ...state,
    diagram: diagramData
  }))
);
