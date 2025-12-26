import { ElementRef } from '@angular/core';
import { YamlDirective } from './yaml.directive';

describe('YamlDirective', () => {
  it('should create an instance', () => {
    const directive = new YamlDirective(new ElementRef(document.createElement('div')));
    expect(directive).toBeTruthy();
  });
});
