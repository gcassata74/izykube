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

class MockNotificationService {
  success() {}
  warn() {}
  error() {}
}

describe('DiagramComponent', () => {
  let component: DiagramComponent;
  let fixture: ComponentFixture<DiagramComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [DiagramComponent],
      imports: [CommonModule, FormsModule],
      providers: [
        { provide: IconService, useClass: MockIconService },
        { provide: DiagramService, useClass: MockDiagramService },
        { provide: Store, useClass: MockStore },
        { provide: AiAssistantService, useClass: MockAiAssistantService },
        { provide: NotificationService, useClass: MockNotificationService }
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
});
