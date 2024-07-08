import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IngressFormComponent } from './ingress-form.component';

describe('IngressFormComponent', () => {
  let component: IngressFormComponent;
  let fixture: ComponentFixture<IngressFormComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [IngressFormComponent]
    });
    fixture = TestBed.createComponent(IngressFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
