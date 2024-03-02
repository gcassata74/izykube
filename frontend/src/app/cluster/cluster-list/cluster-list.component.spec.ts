import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClusterListComponent } from './clusterDTO-list.component';

describe('ClusterListComponent', () => {
  let component: ClusterListComponent;
  let fixture: ComponentFixture<ClusterListComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ClusterListComponent]
    });
    fixture = TestBed.createComponent(ClusterListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
