import { TestBed } from '@angular/core/testing';

import { NodeFactoryService } from './node.factory.service';

describe('NodeFactoryService', () => {
  let service: NodeFactoryService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(NodeFactoryService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
