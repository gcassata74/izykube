import { DiagramService } from './diagram.service';
import { Observable, Subscription, debounceTime, distinctUntilChanged, tap, withLatestFrom } from 'rxjs';
import { Injectable, OnDestroy } from '@angular/core';
import { FormGroup, FormGroupName } from '@angular/forms';

@Injectable()
export class AutoSaveService {

  subscription: Subscription = new Subscription();

  constructor(
    private diagramService: DiagramService
    
  ) {}

  enableAutoSave(form: FormGroup, nodeId: string, change$: Observable<Event>) {
    this.subscription.add(change$.pipe(
      debounceTime(500),
      distinctUntilChanged(),
    ).subscribe(formValue => {
      this.diagramService.updateClusterNodes(nodeId, formValue);
    }));
  }


  ngOnDestroy(): void {
   this.subscription.unsubscribe();
  }

}
