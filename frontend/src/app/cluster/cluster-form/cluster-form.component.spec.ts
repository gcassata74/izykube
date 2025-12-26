import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';

import { ClusterFormComponent } from './cluster-form.component';
import { ClusterService } from '../../services/cluster.service';
import { NotificationService } from '../../services/notification.service';

describe('ClusterFormComponent', () => {
  let component: ClusterFormComponent;
  let fixture: ComponentFixture<ClusterFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, RouterTestingModule],
      declarations: [ClusterFormComponent],
      providers: [
        { provide: ClusterService, useValue: { getCluster: () => of({}), saveCluster: () => of({}), patchCluster: () => of({}) } },
        { provide: NotificationService, useValue: { success: () => {}, warn: () => {}, error: () => {} } },
        { provide: ActivatedRoute, useValue: { paramMap: of({ get: () => null }) } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(ClusterFormComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
