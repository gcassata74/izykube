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
