import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigMapFormComponent } from './config-map-form.component';

describe('ConfigMapFormComponent', () => {
  let component: ConfigMapFormComponent;
  let fixture: ComponentFixture<ConfigMapFormComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ConfigMapFormComponent]
    });
    fixture = TestBed.createComponent(ConfigMapFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
