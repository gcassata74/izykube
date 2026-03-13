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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { EMPTY, of } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DropdownModule } from 'primeng/dropdown';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { OverlayPanelModule } from 'primeng/overlaypanel';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { DiagramComponent } from './diagram.component';
import { IconService } from '../services/icon.service';
import { DiagramService } from '../services/diagram.service';
import { Store } from '@ngrx/store';
import { AiAssistantService } from '../services/ai-assistant.service';
import { NotificationService } from '../services/notification.service';
import { PodShellService } from '../services/pod-shell.service';
import { ClusterStatusEnum } from '../cluster/enum/cluster.-status-enum';
import { ConfigurationChangeService } from '../services/configuration-change.service';
import { ResourceSyncService } from '../services/resource-sync.service';
import { KubeExplorerService } from '../services/kube-explorer.service';
import { ClusterService } from '../services/cluster.service';
import { LinkUpdateService } from '../services/link-update.service';

class MockIconService {
  getIconPath(name: string) {
    return `assets/${name}.svg`;
  }
}

class MockDiagramService {
  addClusterNode() {}
  removeClusterNode() {}
  updateClusterNodes() {}
  setSelectedNode() {}
  clearSelectedNode() {}
}

class MockStore {
  pipe() {
    return of(null);
  }
  select() {
    return of(null);
  }
  dispatch() {}
}

class MockAiAssistantService {
  generate() {
    return of({ content: '{"nodes":[]}', task: 'diagram_nodes' });
  }
  chat() {
    return of({ messages: [{ role: 'assistant', content: 'Hello' }] });
  }
}

describe('DiagramComponent', () => {
  let component: DiagramComponent;
  let fixture: ComponentFixture<DiagramComponent>;
  let notificationService: jasmine.SpyObj<NotificationService>;

  afterEach(() => {
    fixture?.destroy();
  });

  beforeEach(() => {
    notificationService = jasmine.createSpyObj('NotificationService', ['success', 'warn', 'error']);
    TestBed.configureTestingModule({
      declarations: [DiagramComponent],
      imports: [
        CommonModule,
        FormsModule,
        NoopAnimationsModule,
        ButtonModule,
        DialogModule,
        DropdownModule,
        InputTextareaModule,
        OverlayPanelModule,
        ProgressSpinnerModule
      ],
      providers: [
        { provide: IconService, useClass: MockIconService },
        { provide: DiagramService, useClass: MockDiagramService },
        { provide: Store, useClass: MockStore },
        { provide: AiAssistantService, useClass: MockAiAssistantService },
        { provide: NotificationService, useValue: notificationService },
        { provide: PodShellService, useValue: jasmine.createSpyObj('PodShellService', ['getPodsByDeployment', 'createShellSocket']) },
        { provide: KubeExplorerService, useValue: { getWorkloadHealth: () => of([]) } },
        { provide: ClusterService, useValue: { saveCluster: () => of({}) } },
        { provide: LinkUpdateService, useValue: { redraw$: EMPTY } },
        { provide: ConfigurationChangeService, useValue: { emit: jasmine.createSpy('emit') } },
        { provide: ResourceSyncService, useValue: { isRestarting: () => false } }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });
    fixture = TestBed.createComponent(DiagramComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    (component as any).podShellOverlay = { hide: () => {}, show: () => {} };
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose badge metadata for init and sidecar containers', () => {
    const initNode: any = { id: 'n1', name: 'init', type: 'container', icon: '', x: 0, y: 0, role: 'INIT' };
    const sidecarNode: any = { id: 'n2', name: 'sidecar', type: 'container', icon: '', x: 0, y: 0, role: 'SIDECAR' };
    const mainNode: any = { id: 'n3', name: 'main', type: 'container', icon: '', x: 0, y: 0 };

    expect(component.getContainerBadge(initNode)).toEqual(jasmine.objectContaining({ label: 'I' }));
    expect(component.getContainerBadge(sidecarNode)).toEqual(jasmine.objectContaining({ label: 'S' }));
    expect(component.getContainerBadge(mainNode)).toBeNull();
  });

  it('should default attached containers with missing role to sidecar', () => {
    const deploymentNode: any = { id: 'dep-1', name: 'web', type: 'deployment', icon: '', x: 0, y: 0 };
    const containerNode: any = { id: 'c-1', name: 'helper', type: 'container', icon: '', x: 0, y: 0 };
    (component as any).nodes = [deploymentNode, containerNode];
    (component as any).links = [{ id: 'l-1', from: 'c-1', to: 'dep-1', type: 'Container' }];

    expect(component.getContainerBadge(containerNode)).toEqual(jasmine.objectContaining({ label: 'S' }));
  });

  it('should prevent linking containers to non-deployment nodes', () => {
    (component as any).nodes = [
      { id: 'container-1', name: 'c1', type: 'container', icon: '', x: 0, y: 0 },
      { id: 'service-1', name: 'svc', type: 'service', icon: '', x: 0, y: 0 }
    ];
    (component as any).links = [];

    (component as any).createLinkWithPoints(
      'container-1',
      'service-1',
      { side: 'top', x: 0, y: 0 },
      { side: 'top', x: 0, y: 0 }
    );

    expect(notificationService.warn).toHaveBeenCalledWith(
      'Invalid connection',
      'Containers can only be linked to Deployments or Config Bundles.'
    );
    expect((component as any).links.length).toBe(0);
  });

  it('should determine pod shell trigger visibility based on namespace status', () => {
    const deploymentNode: any = { id: 'd1', name: 'web', type: 'deployment', icon: '', x: 0, y: 0 };
    const serviceNode: any = { id: 's1', name: 'svc', type: 'service', icon: '', x: 0, y: 0 };
    (component as any).currentClusterSnapshot = { status: ClusterStatusEnum.DEPLOYED };
    expect(component.shouldShowPodShellTrigger(deploymentNode)).toBeTrue();
    expect(component.shouldShowPodShellTrigger(serviceNode)).toBeFalse();

    (component as any).currentClusterSnapshot = { status: ClusterStatusEnum.CREATED };
    expect(component.shouldShowPodShellTrigger(deploymentNode)).toBeFalse();
  });

  it('should fetch pods when shell icon is clicked', () => {
    const podShellService = TestBed.inject(PodShellService) as jasmine.SpyObj<PodShellService>;
    podShellService.getPodsByDeployment.and.returnValue(of([]));
    (component as any).currentClusterSnapshot = { nameSpace: 'demo', status: ClusterStatusEnum.DEPLOYED };
    const node: any = { id: 'd1', name: 'web', type: 'deployment', icon: '', x: 0, y: 0 };
    const event = {
      stopPropagation: jasmine.createSpy('stopPropagation'),
      preventDefault: jasmine.createSpy('preventDefault')
    } as unknown as MouseEvent;

    component.onPodShellIconClick(event, node);

    expect(podShellService.getPodsByDeployment).toHaveBeenCalledWith('demo', 'web');
  });
});
