import { ElementRef } from '@angular/core';
import { BashDirective } from './bash.directive';

describe('BashDirective', () => {
  it('should create an instance', () => {
    const directive = new BashDirective(new ElementRef(document.createElement('div')));
    expect(directive).toBeTruthy();
  });
});
