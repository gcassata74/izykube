import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AssetListComponent } from './assets-list.component';

describe('AssetsListComponent', () => {
  let component: AssetListComponent;
  let fixture: ComponentFixture<AssetListComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [AssetListComponent]
    });
    fixture = TestBed.createComponent(AssetListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
