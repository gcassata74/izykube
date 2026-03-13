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
