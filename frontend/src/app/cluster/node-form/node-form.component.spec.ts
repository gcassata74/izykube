import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NodeFormComponent } from './node-form.component';

describe('NodeFormComponent', () => {
  let component: NodeFormComponent;
  let fixture: ComponentFixture<NodeFormComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [NodeFormComponent]
    });
    fixture = TestBed.createComponent(NodeFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
