import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { provideMockStore } from '@ngrx/store/testing';
import { of } from 'rxjs';

import { AssetListComponent } from './assets-list.component';
import { AssetService } from 'src/app/services/asset.service';
import { NotificationService } from 'src/app/services/notification.service';

describe('AssetsListComponent', () => {
  let component: AssetListComponent;
  let fixture: ComponentFixture<AssetListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      declarations: [AssetListComponent],
      providers: [
        { provide: AssetService, useValue: { getAllAssets: () => of([]), deleteAsset: () => of({}) } },
        { provide: NotificationService, useValue: { success: () => {}, warn: () => {}, error: () => {} } },
        provideMockStore({ initialState: {} }),
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(AssetListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
