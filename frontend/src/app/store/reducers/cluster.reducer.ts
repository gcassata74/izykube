// cluster.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { addNode, removeNode, updateDiagram, updateNodeAsset } from '../actions/cluster.actions';
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
        nodes: state.clusterData.nodes.filter(node => node.id !== nodeId),
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


  on(updateDiagram, (state, { diagramData }) => ({
    ...state,
    diagram: diagramData
  }))
);
