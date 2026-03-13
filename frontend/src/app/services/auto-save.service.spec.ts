/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
