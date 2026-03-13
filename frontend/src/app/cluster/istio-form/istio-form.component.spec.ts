/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IstioFormComponent } from './istio-form.component';
import { ReactiveFormsModule } from '@angular/forms';
import { DropdownModule } from 'primeng/dropdown';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Istio } from '../../model/istio.class';
import { AutoSaveService } from '../../services/auto-save.service';

describe('IstioFormComponent', () => {
  let component: IstioFormComponent;
  let fixture: ComponentFixture<IstioFormComponent>;

  beforeEach(async () => {
    const autoSaveStub = { enableAutoSave: () => {}, flushPendingChanges: () => {} };

    TestBed.configureTestingModule({
      imports: [
        ReactiveFormsModule,
        DropdownModule,
        InputNumberModule,
        InputTextModule,
        NoopAnimationsModule
      ],
      declarations: [IstioFormComponent],
      providers: []
    });

    TestBed.overrideComponent(IstioFormComponent, {
      set: {
        providers: [{ provide: AutoSaveService, useValue: autoSaveStub }],
      },
    });

    await TestBed.compileComponents();

    fixture = TestBed.createComponent(IstioFormComponent);
    component = fixture.componentInstance;
    component.selectedNode = new Istio('istio:test', 'istio', 'example.com', '/', '', 80);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
