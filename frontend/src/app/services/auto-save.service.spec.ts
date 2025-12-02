import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AutoSaveService } from './auto-save.service';
import { DiagramService } from './diagram.service';
import { Store } from '@ngrx/store';
import { ConfigurationChangeService } from './configuration-change.service';

describe('AutoSaveService', () => {
  let service: AutoSaveService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AutoSaveService,
        { provide: DiagramService, useValue: { updateClusterNodes: jasmine.createSpy('updateClusterNodes') } },
        { provide: Store, useValue: { select: () => of(null) } },
        { provide: ConfigurationChangeService, useValue: { emit: jasmine.createSpy('emit') } }
      ]
    });
    service = TestBed.inject(AutoSaveService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
