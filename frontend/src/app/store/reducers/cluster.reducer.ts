// cluster.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { addLink, addNode, removeLink, removeNode, updateDiagram, updateNodeAsset } from '../actions/cluster.actions';
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

  on(updateNodeAsset, (state, { nodeId, assetId }) => ({
      ...state,
      clusterData: {
        ...state.clusterData,
        nodes: state.clusterData.nodes.map(node =>
          node.id === nodeId ? { ...node, assetId: assetId } : node
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
