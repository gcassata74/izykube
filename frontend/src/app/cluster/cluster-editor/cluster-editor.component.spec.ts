import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClusterEditorComponent } from './cluster-editor.component';

describe('ClusterEditorComponent', () => {
  let component: ClusterEditorComponent;
  let fixture: ComponentFixture<ClusterEditorComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ClusterEditorComponent]
    });
    fixture = TestBed.createComponent(ClusterEditorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
