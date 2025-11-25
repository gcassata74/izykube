import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { DiagramComponent } from './diagram.component';
import { IconService } from '../services/icon.service';
import { DiagramService } from '../services/diagram.service';
import { Store } from '@ngrx/store';
import { AiAssistantService } from '../services/ai-assistant.service';
import { NotificationService } from '../services/notification.service';

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

  beforeEach(() => {
    notificationService = jasmine.createSpyObj('NotificationService', ['success', 'warn', 'error']);
    TestBed.configureTestingModule({
      declarations: [DiagramComponent],
      imports: [CommonModule, FormsModule],
      providers: [
        { provide: IconService, useClass: MockIconService },
        { provide: DiagramService, useClass: MockDiagramService },
        { provide: Store, useClass: MockStore },
        { provide: AiAssistantService, useClass: MockAiAssistantService },
        { provide: NotificationService, useValue: notificationService }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });
    fixture = TestBed.createComponent(DiagramComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
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

    expect(notificationService.warn).toHaveBeenCalledWith('Invalid connection', 'Containers can only be linked to Deployments.');
    expect((component as any).links.length).toBe(0);
  });
});
