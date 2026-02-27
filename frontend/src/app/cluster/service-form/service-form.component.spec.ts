/**
 * IzyKube - Enterprise Kubernetes Management Platform
 * Copyright (C) 2024 IzyLife Corporation. All rights reserved.
 * 
 * This file is part of IzyKube, an enterprise Kubernetes management platform
 * developed by IzyLife Corporation. Unauthorized copying or redistribution of this file 
 * in source and binary forms via any medium is strictly prohibited.
 * 
 * IzyKube is proprietary software of IzyLife Corporation. 
 * No warranty, explicit or implicit, provided.
 * 
 * @author IzyLife Development Team
 * @version 1.0.0
 * @since March 2024
 */
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
