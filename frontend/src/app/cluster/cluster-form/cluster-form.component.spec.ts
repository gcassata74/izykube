import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClusterFormComponent } from './cluster-form.component';

describe('ClusterFormComponent', () => {
  let component: ClusterFormComponent;
  let fixture: ComponentFixture<ClusterFormComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ClusterFormComponent]
    });
    fixture = TestBed.createComponent(ClusterFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
