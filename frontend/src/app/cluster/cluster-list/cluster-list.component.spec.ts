import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of } from 'rxjs';
import { provideMockStore } from '@ngrx/store/testing';
import { ConfirmationService } from 'primeng/api';
import { ClusterListComponent } from './cluster-list.component';
import { ClusterService } from '../../services/cluster.service';
import { TemplateService } from '../../services/template.service';
import { NotificationService } from '../../services/notification.service';
import { AiAssistantService } from '../../services/ai-assistant.service';
import { getClusters } from '../../store/selectors/selectors';

describe('ClusterListComponent', () => {
  let component: ClusterListComponent;
  let fixture: ComponentFixture<ClusterListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      declarations: [ClusterListComponent],
      providers: [
        {
          provide: ClusterService,
          useValue: {
            getAllClusters: () => of([]),
            deploy: () => of({}),
            undeploy: () => of({}),
            deleteCluster: () => of({}),
            patchCluster: () => of({}),
            getCluster: () => of({ status: 'READY_FOR_DEPLOYMENT' })
          }
        },
        { provide: TemplateService, useValue: { createTemplate: () => of({}), deleteTemplate: () => of({}) } },
        { provide: NotificationService, useValue: { success: () => {}, error: () => {}, warn: () => {} } },
        { provide: AiAssistantService, useValue: { exportHelmChart: () => of({}), exportYaml: () => of({}) } },
        { provide: ConfirmationService, useValue: { confirm: () => {} } },
        provideMockStore({ selectors: [{ selector: getClusters, value: [] }] })
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ClusterListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
