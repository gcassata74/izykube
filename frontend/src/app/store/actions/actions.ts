import { createAction, props } from "@ngrx/store";

export const INIT = '@ngrx/store/init';
export const SET_CURRENT_ACTION = '[Toolbar] Set Current Action';
export const UPDATE_DIAGRAM_MODEL = '[Diagram] Update Model';

export const Init = createAction(INIT);

export const setCurrentAction = createAction(
    SET_CURRENT_ACTION,
    props<{ action: string }>()
  );




