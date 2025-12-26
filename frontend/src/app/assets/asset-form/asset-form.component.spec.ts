import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterTestingModule } from '@angular/router/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { AssetFormComponent } from './asset-form.component';
import { AssetService } from 'src/app/services/asset.service';
import { NotificationService } from 'src/app/services/notification.service';

describe('AssetFormComponent', () => {
  let component: AssetFormComponent;
  let fixture: ComponentFixture<AssetFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, RouterTestingModule],
      declarations: [AssetFormComponent],
      providers: [
        { provide: AssetService, useValue: { getAsset: () => of(null), saveAsset: () => of(null) } },
        { provide: NotificationService, useValue: { success: () => {}, warn: () => {}, error: () => {} } },
        { provide: ActivatedRoute, useValue: { paramMap: of({ get: () => null }) } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(AssetFormComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
