import { Injectable } from "@angular/core";
import { Actions, createEffect, ofType } from "@ngrx/effects";


@Injectable()
export class InitEffect {

  constructor(
    private actions$: Actions,
  ) {}


}
