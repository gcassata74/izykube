import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeploymentFormComponent } from './deployment-form.component';

describe('DeploymentFormComponent', () => {
  let component: DeploymentFormComponent;
  let fixture: ComponentFixture<DeploymentFormComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [DeploymentFormComponent]
    });
    fixture = TestBed.createComponent(DeploymentFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
