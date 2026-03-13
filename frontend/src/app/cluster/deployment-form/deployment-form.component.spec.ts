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

import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { of } from 'rxjs';

import { DeploymentFormComponent } from './deployment-form.component';
import { AutoSaveService } from '../../services/auto-save.service';
import { AssetService } from '../../services/asset.service';
import { NotificationService } from '../../services/notification.service';
import { KubeExplorerService } from '../../services/kube-explorer.service';

describe('DeploymentFormComponent', () => {
  let component: DeploymentFormComponent;
  let fixture: ComponentFixture<DeploymentFormComponent>;

  beforeEach(async () => {
    const autoSaveStub = { enableAutoSave: () => {}, flushPendingChanges: () => {} };

    TestBed.configureTestingModule({
      imports: [ReactiveFormsModule],
      declarations: [DeploymentFormComponent],
      providers: [
        { provide: AssetService, useValue: { getAssets: () => of([]) } },
        { provide: NotificationService, useValue: { success: () => {}, warn: () => {}, error: () => {} } },
        { provide: KubeExplorerService, useValue: { setDeploymentMesh: () => of({}) } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    TestBed.overrideComponent(DeploymentFormComponent, {
      set: {
        providers: [{ provide: AutoSaveService, useValue: autoSaveStub }],
      },
    });

    await TestBed.compileComponents();

    fixture = TestBed.createComponent(DeploymentFormComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
