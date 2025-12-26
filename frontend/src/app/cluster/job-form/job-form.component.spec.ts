import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { of } from 'rxjs';

import { JobFormComponent } from './job-form.component';
import { AutoSaveService } from '../../services/auto-save.service';
import { AssetService } from '../../services/asset.service';
import { NotificationService } from '../../services/notification.service';

describe('JobFormComponent', () => {
  let component: JobFormComponent;
  let fixture: ComponentFixture<JobFormComponent>;

  beforeEach(async () => {
    const autoSaveStub = { enableAutoSave: () => {}, flushPendingChanges: () => {} };

    TestBed.configureTestingModule({
      imports: [ReactiveFormsModule],
      declarations: [JobFormComponent],
      providers: [
        { provide: AssetService, useValue: { getAssets: () => of([]) } },
        { provide: NotificationService, useValue: { success: () => {}, warn: () => {}, error: () => {} } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    TestBed.overrideComponent(JobFormComponent, {
      set: {
        providers: [{ provide: AutoSaveService, useValue: autoSaveStub }],
      },
    });

    await TestBed.compileComponents();

    fixture = TestBed.createComponent(JobFormComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
