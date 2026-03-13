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
