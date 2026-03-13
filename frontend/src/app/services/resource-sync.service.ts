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

import { Injectable, OnDestroy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Subject, Subscription, EMPTY, of } from 'rxjs';
import { catchError, concatMap, finalize, map, switchMap, take, tap } from 'rxjs/operators';
import { NamespaceService } from './namespace.service';
import { NotificationService } from './notification.service';
import { getCurrentCluster, getNodeById } from '../store/selectors/selectors';
import { ClusterStatusEnum } from '../cluster/enum/cluster.-status-enum';
import { updateNode } from '../store/actions/actions';
import { ConfigurationChangeEvent, ConfigurationChangeService } from './configuration-change.service';
import { ResourceSyncStatus } from '../model/resource-sync-status';

@Injectable({
  providedIn: 'root'
})
export class ResourceSyncService implements OnDestroy {

  private readonly restartQueue$ = new Subject<ConfigurationChangeEvent>();
  private readonly subscription = new Subscription();
  private readonly restartingResources = new Set<string>();
  private readonly queuedEvents = new Map<string, ConfigurationChangeEvent>();

  constructor(
    private readonly configurationChangeService: ConfigurationChangeService,
    private readonly namespaceService: NamespaceService,
    private readonly notificationService: NotificationService,
    private readonly store: Store
  ) {
    this.subscription.add(
      this.configurationChangeService.configurationChanged$.subscribe(event => this.enqueue(event))
    );

    this.subscription.add(
      this.restartQueue$.pipe(concatMap(event => this.executeRestart(event))).subscribe()
    );
  }

  isRestarting(resourceId: string): boolean {
    return this.restartingResources.has(resourceId);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private enqueue(event: ConfigurationChangeEvent): void {
    if (this.restartingResources.has(event.resourceId)) {
      this.queuedEvents.set(event.resourceId, event);
      return;
    }
    this.restartQueue$.next(event);
  }

  private executeRestart(event: ConfigurationChangeEvent) {
    this.restartingResources.add(event.resourceId);

    return this.store.select(getCurrentCluster).pipe(
      take(1),
      switchMap(cluster => {
        if (!cluster || cluster.status !== ClusterStatusEnum.DEPLOYED || !cluster.nameSpace) {
          this.finish(event.resourceId);
          return EMPTY;
        }

        return this.store.select(getNodeById(event.resourceId)).pipe(
          take(1),
          switchMap(node => {
            if (!node) {
              this.finish(event.resourceId);
              return EMPTY;
            }
            return this.namespaceService.restartResource(cluster.nameSpace, event.resourceId, node).pipe(
              tap(response => this.handleSuccess(event.resourceId, response)),
              catchError(error => {
                console.error('Resource restart failed', error);
                this.notificationService.error('Restart failed', error?.error?.message || 'Unable to restart resource');
                return of(undefined);
              }),
              finalize(() => this.finish(event.resourceId)),
              map(() => void 0)
            );
          })
        );
      })
    );
  }

  private handleSuccess(resourceId: string, response?: ResourceSyncStatus) {
    if (response?.synced) {
      this.store.dispatch(updateNode({ nodeId: resourceId, formValues: { isAffected: false } }));
    } else if (response?.message) {
      this.notificationService.warn(
        $localize`:@@resourceSync.warn.restartResultTitle:Restart result`,
        response.message
      );
    }
  }

  private finish(resourceId: string): void {
    this.restartingResources.delete(resourceId);
    const pending = this.queuedEvents.get(resourceId);
    if (pending) {
      this.queuedEvents.delete(resourceId);
      this.enqueue(pending);
    }
  }
}
