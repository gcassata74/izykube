// toolbar.service.ts
import { BehaviorSubject } from 'rxjs';

export interface Button {
  name: string;
  route: string;
}



export class ToolbarService {
  private buttonsSource = new BehaviorSubject<Button[]>([]);
  buttons$ = this.buttonsSource.asObservable();

  setButtons(buttons: Button[]) {
    this.buttonsSource.next(buttons);
  }
}


