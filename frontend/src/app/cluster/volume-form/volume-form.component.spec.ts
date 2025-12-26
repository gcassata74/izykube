import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { of } from 'rxjs';

import { VolumeFormComponent } from './volume-form.component';
import { AutoSaveService } from '../../services/auto-save.service';
import { PersistentVolumeService } from '../../services/persistent-volume.service';

describe('VolumeFormComponent', () => {
  let component: VolumeFormComponent;
  let fixture: ComponentFixture<VolumeFormComponent>;

  beforeEach(async () => {
    const autoSaveStub = { enableAutoSave: () => {}, flushPendingChanges: () => {} };

    TestBed.configureTestingModule({
      imports: [ReactiveFormsModule],
      declarations: [VolumeFormComponent],
      providers: [
        { provide: PersistentVolumeService, useValue: { getVolumes: () => of([]) } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    TestBed.overrideComponent(VolumeFormComponent, {
      set: {
        providers: [{ provide: AutoSaveService, useValue: autoSaveStub }],
      },
    });

    await TestBed.compileComponents();

    fixture = TestBed.createComponent(VolumeFormComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
