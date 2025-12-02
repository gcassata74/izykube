import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

export interface ConfigurationChangeEvent {
  resourceId: string;
}

@Injectable({
  providedIn: 'root'
})
export class ConfigurationChangeService {

  private readonly change$ = new Subject<ConfigurationChangeEvent>();
  readonly configurationChanged$ = this.change$.asObservable();

  emit(event: ConfigurationChangeEvent): void {
    this.change$.next(event);
  }
}
