// cluster.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { addNode, removeNode, updateDiagram } from '../actions/cluster.actions';
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


  on(updateDiagram, (state, { diagramData }) => ({
    ...state,
    diagram: diagramData
  }))
);
