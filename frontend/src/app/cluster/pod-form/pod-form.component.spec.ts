import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PodFormComponent } from './pod-form.component';

describe('PodFormComponent', () => {
  let component: PodFormComponent;
  let fixture: ComponentFixture<PodFormComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [PodFormComponent]
    });
    fixture = TestBed.createComponent(PodFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
