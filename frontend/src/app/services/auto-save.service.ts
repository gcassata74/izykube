import { DiagramService } from './diagram.service';
import { Observable, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { Injectable, OnDestroy } from '@angular/core';
import { FormGroup } from '@angular/forms';

@Injectable()
export class AutoSaveService {

  subscription: Subscription = new Subscription();

  constructor(
    private diagramService: DiagramService
    
  ) {}

  enableAutoSave(form: FormGroup, nodeId: string, change$: Observable<any>) {
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
