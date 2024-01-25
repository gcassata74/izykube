// toolbar.service.ts
import { BehaviorSubject } from 'rxjs';
import { Button } from '../model/button.class';



export class ToolbarService {
  private buttonsSource = new BehaviorSubject<Button[]>([]);
  buttons$ = this.buttonsSource.asObservable();

  setButtons(buttons: Button[]) {
    this.buttonsSource.next(buttons);
  }
}


