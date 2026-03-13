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
import { ActivatedRoute } from '@angular/router';
import { provideMockStore } from '@ngrx/store/testing';
import { of } from 'rxjs';
import { ClusterEditorComponent } from './cluster-editor.component';
import { ToolbarService } from '../../services/toolbar.service';
import { DiagramService } from '../../services/diagram.service';
import { NotificationService } from '../../services/notification.service';
import { ClusterService } from '../../services/cluster.service';
import { TemplateService } from '../../services/template.service';

describe('ClusterEditorComponent', () => {
  let component: ClusterEditorComponent;
  let fixture: ComponentFixture<ClusterEditorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ClusterEditorComponent],
      providers: [
        ToolbarService,
        provideMockStore({ initialState: {} }),
        { provide: DiagramService, useValue: { selectedLinkId$: of(null), selectedNodeId$: of(null) } },
        { provide: NotificationService, useValue: { success: () => {}, warn: () => {}, error: () => {} } },
        { provide: ClusterService, useValue: { getCluster: () => of({}), patchCluster: () => of({}), saveCluster: () => of({}) } },
        { provide: TemplateService, useValue: { createTemplate: () => of({}), deleteTemplate: () => of({}) } },
        { provide: ActivatedRoute, useValue: { params: of({ id: '1' }) } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(ClusterEditorComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
