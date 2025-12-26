import { TestBed } from '@angular/core/testing';
import { provideMockStore } from '@ngrx/store/testing';

import { ClusterService } from './cluster.service';
import { DataService } from './data.service';
import { Router } from '@angular/router';

describe('ClusterService', () => {
  let service: ClusterService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ClusterService,
        provideMockStore(),
        { provide: DataService, useValue: { get: () => {}, post: () => {}, put: () => {}, patch: () => {}, delete: () => {} } },
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
      ],
    });
    service = TestBed.inject(ClusterService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
