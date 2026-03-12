/* SPDX-License-Identifier: AGPL-3.0-or-later */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';

import { ServiceFormComponent } from './service-form.component';
import { AutoSaveService } from '../../services/auto-save.service';
import { NotificationService } from '../../services/notification.service';
import { PortForwardService } from '../../services/port-forward.service';

describe('ServiceFormComponent', () => {
  let component: ServiceFormComponent;
  let fixture: ComponentFixture<ServiceFormComponent>;

  beforeEach(async () => {
    const autoSaveStub = { enableAutoSave: () => {}, flushPendingChanges: () => {} };
    const notificationStub = { success: () => {}, warn: () => {}, error: () => {} };
    const portForwardStub = { startForward: () => ({ subscribe: () => {} }), stopForward: () => ({ subscribe: () => {} }) };

    TestBed.configureTestingModule({
      imports: [ReactiveFormsModule],
      declarations: [ServiceFormComponent],
      schemas: [NO_ERRORS_SCHEMA],
    });

    TestBed.overrideComponent(ServiceFormComponent, {
      set: {
        providers: [
          { provide: AutoSaveService, useValue: autoSaveStub },
          { provide: NotificationService, useValue: notificationStub },
          { provide: PortForwardService, useValue: portForwardStub }
        ],
      },
    });

    await TestBed.compileComponents();

    fixture = TestBed.createComponent(ServiceFormComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
