export interface MainState {
   buttons: any;
  }
  
  export interface State {
    mainState: MainState;
  }
  
  export const initialState: State = {
    mainState: {
        buttons: [],
    }
  }