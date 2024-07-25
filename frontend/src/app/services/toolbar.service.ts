// toolbar.service.ts
import { BehaviorSubject, delay, Subject } from 'rxjs';
import { Button } from '../model/button.interface';
import { Injectable } from '@angular/core';


@Injectable({
  providedIn: 'root'
})
export class ToolbarService {
  private buttonsSource = new BehaviorSubject<Button[]>([]);
  buttons$ = this.buttonsSource.asObservable().pipe(
    delay(0)
  );

  setButtons(buttons: Button[]) {
    this.buttonsSource.next(buttons);
  }

  clearButtons() {
    this.buttonsSource.next([]);
  }

  
}


