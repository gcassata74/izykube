import { DiagramService } from './diagram.service';
import { Observable, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
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
    void form;
    this.subscription.add(change$.pipe(
      debounceTime(500),
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
