import { Injectable } from '@angular/core';
import { catchError, exhaustMap, map, Observable, of } from 'rxjs';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Action } from '@ngrx/store';
import * as actions from '../actions/actions';

@Injectable()
export class InitEffect {

  constructor(
    private actions$: Actions,
  ) {}

 
}
