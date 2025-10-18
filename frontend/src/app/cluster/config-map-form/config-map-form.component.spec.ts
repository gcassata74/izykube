import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ConfigMapFormComponent } from './config-map-form.component';
import { AutoSaveService } from '../../services/auto-save.service';
import { NotificationService } from '../../services/notification.service';
import { ConfigMap } from '../../model/config-map.class';
import { AiAssistantService } from '../../services/ai-assistant.service';
import { of } from 'rxjs';

class MockAutoSaveService {
  enableAutoSave() {}
}

class MockNotificationService {
  success() {}
  warn() {}
  error() {}
}

class MockAiAssistantService {
  generate() {
    return of({ content: 'key: value', task: 'configmap_yaml' });
  }
}

describe('ConfigMapFormComponent', () => {
  let component: ConfigMapFormComponent;
  let fixture: ComponentFixture<ConfigMapFormComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ConfigMapFormComponent],
      imports: [ReactiveFormsModule, FormsModule],
      providers: [
        { provide: AutoSaveService, useClass: MockAutoSaveService },
        { provide: NotificationService, useClass: MockNotificationService },
        { provide: AiAssistantService, useClass: MockAiAssistantService }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });
    fixture = TestBed.createComponent(ConfigMapFormComponent);
    component = fixture.componentInstance;
    component.selectedNode = new ConfigMap('1', 'test-config', 'apiVersion: v1');
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
