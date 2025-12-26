import { TestBed } from '@angular/core/testing';
import { provideMockStore } from '@ngrx/store/testing';

import { DiagramService } from './diagram.service';
import { NodeFactoryService } from './node.factory.service';

describe('DiagramService', () => {
  let service: DiagramService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideMockStore(),
        { provide: NodeFactoryService, useValue: { createNode: () => ({}) } },
      ],
    });
    service = TestBed.inject(DiagramService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
