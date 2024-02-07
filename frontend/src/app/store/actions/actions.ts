import { createAction, props } from "@ngrx/store";

export const INIT = '@ngrx/store/init';
export const SET_CURRENT_ACTION = '[Toolbar] Set Current Action';
export const RESET_CURRENT_ACTION = '[Toolbar] Reset Current Action';

export const Init = createAction(INIT);

export const setCurrentAction = createAction(
    SET_CURRENT_ACTION,
    props<{ action: string }>()
  );

  export const resetCurrentAction = createAction(
    RESET_CURRENT_ACTION
  );  




