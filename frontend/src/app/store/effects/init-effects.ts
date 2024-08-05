import { ClusterService } from './../../services/cluster.service';
import { Injectable } from "@angular/core";
import { Actions, createEffect, ofType } from "@ngrx/effects";
import { Store } from "@ngrx/store";
import { catchError, map, mergeMap, of, switchMap, take, tap, withLatestFrom } from "rxjs";
import { getCurrentCluster } from "../selectors/selectors";
import * as actions from '../actions/actions';
import { Cluster } from 'src/app/model/cluster.class';


@Injectable()
export class InitEffects {

  constructor(
    private actions$: Actions, 
    private store: Store,
    private clusterService: ClusterService
    ) {}

 

}
