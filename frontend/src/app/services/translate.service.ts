import { ɵ$localize } from '@angular/localize';
import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class TranslateService {

  translate(key: string): string {
    // The $localize tag function will be replaced by the actual translation at build time
    return ɵ$localize `:@@${key}:`;
  }
}
