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
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { KubeExplorerService } from './kube-explorer.service';

describe('KubeExplorerService', () => {
  let service: KubeExplorerService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [KubeExplorerService],
    });
    service = TestBed.inject(KubeExplorerService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('calls the pod endpoint', () => {
    service.getPod('default', 'mypod').subscribe();

    const req = httpMock.expectOne('/api/v1/namespaces/default/pods/mypod');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('calls the pod logs endpoint with container and tail', () => {
    service.getPodLogsV1('default', 'mypod', 'app', 123).subscribe();

    const req = httpMock.expectOne(r =>
      r.url === '/api/v1/namespaces/default/pods/mypod/log'
      && r.params.get('container') === 'app'
      && r.params.get('tailLines') === '123'
    );
    expect(req.request.method).toBe('GET');
    req.flush('');
  });

  it('calls the pod events endpoint', () => {
    service.getPodEvents('default', 'mypod').subscribe();

    const req = httpMock.expectOne(r =>
      r.url === '/api/v1/namespaces/default/events'
      && (r.params.get('fieldSelector') || '').includes('involvedObject.name=mypod')
    );
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
