// toolbar.service.ts
import { BehaviorSubject, Subject } from 'rxjs';
import { Button } from '../model/button.interface';



export class ToolbarService {
  private buttonsSource = new BehaviorSubject<Button[]>([]);
  buttons$ = this.buttonsSource.asObservable();

  setButtons(buttons: Button[]) {
    this.buttonsSource.next(buttons);
  }
}


