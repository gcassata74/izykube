/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import { DiagramService } from './diagram.service';
import { Observable, Subscription, debounceTime, distinctUntilChanged, filter } from 'rxjs';
import { Injectable, OnDestroy } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { Store } from '@ngrx/store';
import { getCurrentCluster } from '../store/selectors/selectors';
import { ClusterStatusEnum } from '../cluster/enum/cluster.-status-enum';
import { ConfigurationChangeService } from './configuration-change.service';
import { take } from 'rxjs/operators';

@Injectable()
export class AutoSaveService {

  subscription: Subscription = new Subscription();

  constructor(
    private diagramService: DiagramService,
    private store: Store,
    private configurationChangeService: ConfigurationChangeService
  ) {}

  enableAutoSave(form: FormGroup, nodeId: string, change$: Observable<any>) {
    this.subscription.add(change$.pipe(
      debounceTime(500),
      filter(() => form.valid),
      distinctUntilChanged(),
    ).subscribe(formValue => {
      this.persistNodeChanges(nodeId, formValue);
    }));
  }

  flushPendingChanges(nodeId: string, payload: any): void {
    this.persistNodeChanges(nodeId, payload);
  }

  private persistNodeChanges(nodeId: string, formValue: any): void {
    this.store.select(getCurrentCluster).pipe(take(1)).subscribe(cluster => {
      const shouldAutoSync = cluster?.status === ClusterStatusEnum.DEPLOYED;
      const values = formValue ?? {};
      const payload = shouldAutoSync ? { ...values, isAffected: true } : values;
      this.diagramService.updateClusterNodes(nodeId, payload);
      if (shouldAutoSync) {
        this.configurationChangeService.emit({ resourceId: nodeId });
      }
    });
  }

  ngOnDestroy(): void {
   this.subscription.unsubscribe();
  }

}
