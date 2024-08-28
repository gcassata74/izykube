import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VolumeFormComponent } from './volume-form.component';

describe('VolumeFormComponent', () => {
  let component: VolumeFormComponent;
  let fixture: ComponentFixture<VolumeFormComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [VolumeFormComponent]
    });
    fixture = TestBed.createComponent(VolumeFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
