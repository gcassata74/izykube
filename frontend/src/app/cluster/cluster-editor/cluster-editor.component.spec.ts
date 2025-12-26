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
