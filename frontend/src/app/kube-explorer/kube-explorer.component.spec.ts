import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';

import { KubeExplorerComponent } from './kube-explorer.component';
import { KubeExplorerService } from '../services/kube-explorer.service';
import { NotificationService } from '../services/notification.service';
import { PortForwardService } from '../services/port-forward.service';

describe('KubeExplorerComponent (row actions)', () => {
  let fixture: ComponentFixture<KubeExplorerComponent>;
  let component: KubeExplorerComponent;
  let kubeExplorerService: jasmine.SpyObj<KubeExplorerService>;

  beforeEach(async () => {
    kubeExplorerService = jasmine.createSpyObj<KubeExplorerService>(
      'KubeExplorerService',
      [
        'getNamespaces',
        'getNamespaceSummary',
        'getPod',
        'getPodLogsV1',
        'getPodEvents',
      ]
    );

    kubeExplorerService.getNamespaces.and.returnValue(of(['default']));
    kubeExplorerService.getNamespaceSummary.and.returnValue(of({
      namespace: 'default',
      pods: [],
      deployments: [],
      services: [],
      routes: [],
      configMaps: [],
      secrets: [],
      jobs: [],
      cronJobs: [],
      daemonSets: [],
      statefulSets: [],
    }));

    await TestBed.configureTestingModule({
      declarations: [KubeExplorerComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: KubeExplorerService, useValue: kubeExplorerService },
        { provide: NotificationService, useValue: { error: () => {}, success: () => {}, warn: () => {} } },
        { provide: PortForwardService, useValue: { listActiveForwards: () => of([]), stopForward: () => of({}) } },
        { provide: ChangeDetectorRef, useValue: { markForCheck: () => {} } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(KubeExplorerComponent);
    component = fixture.componentInstance;
  });

  it('opens the logs panel with the selected pod reference', fakeAsync(() => {
    kubeExplorerService.getPod.and.returnValue(of({ spec: { containers: [{ name: 'app' }] } } as any));
    kubeExplorerService.getPodLogsV1.and.returnValue(of('hello'));

    component.openPodLogsFromRow({ namespace: 'default', name: 'mypod' });
    tick();

    expect(kubeExplorerService.getPod).toHaveBeenCalledWith('default', 'mypod');
    expect(kubeExplorerService.getPodLogsV1).toHaveBeenCalledWith('default', 'mypod', 'app');
    expect(component.logsDialogVisible).toBeTrue();
    expect(component.logsContent).toContain('hello');
    expect(component.logsSelectedContainer).toBe('app');
  }));

  it('opens the inspect panel with the selected pod reference', fakeAsync(() => {
    kubeExplorerService.getPod.and.returnValue(of({ metadata: { name: 'mypod', namespace: 'default' }, status: { containerStatuses: [] } } as any));
    kubeExplorerService.getPodEvents.and.returnValue(of([]));

    component.openPodInspectFromRow({ namespace: 'default', name: 'mypod' });
    tick();

    expect(kubeExplorerService.getPod).toHaveBeenCalledWith('default', 'mypod');
    expect(kubeExplorerService.getPodEvents).toHaveBeenCalledWith('default', 'mypod');
    expect(component.inspectDialogVisible).toBeTrue();
    expect(component.inspectedPod?.metadata?.name).toBe('mypod');
  }));
});
