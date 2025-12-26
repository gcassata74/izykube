import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideMockStore } from '@ngrx/store/testing';
import { of } from 'rxjs';

import { NodeFormComponent } from './node-form.component';
import { DiagramService } from '../../services/diagram.service';

describe('NodeFormComponent', () => {
  let component: NodeFormComponent;
  let fixture: ComponentFixture<NodeFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [NodeFormComponent],
      providers: [
        provideMockStore({ initialState: {} }),
        { provide: DiagramService, useValue: { selectedNodeId$: of(null) } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(NodeFormComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
