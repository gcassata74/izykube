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
import { ReactiveFormsModule } from '@angular/forms';
import { DropdownModule } from 'primeng/dropdown';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { IngressFormComponent } from './ingress-form.component';
import { Ingress } from '../../model/ingress.class';
import { AutoSaveService } from '../../services/auto-save.service';

describe('IngressFormComponent', () => {
  let component: IngressFormComponent;
  let fixture: ComponentFixture<IngressFormComponent>;

  beforeEach(async () => {
    const autoSaveStub = { enableAutoSave: () => {}, flushPendingChanges: () => {} };

    TestBed.configureTestingModule({
      imports: [
        ReactiveFormsModule,
        DropdownModule,
        InputNumberModule,
        InputTextModule,
        ButtonModule,
        NoopAnimationsModule
      ],
      declarations: [IngressFormComponent],
      providers: []
    });

    TestBed.overrideComponent(IngressFormComponent, {
      set: {
        providers: [{ provide: AutoSaveService, useValue: autoSaveStub }],
      },
    });

    await TestBed.compileComponents();
    fixture = TestBed.createComponent(IngressFormComponent);
    component = fixture.componentInstance;
    component.selectedNode = new Ingress('ingress:test', 'test', 'example.com', '/', '', 80);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('initializes tls and annotation controls', () => {
    expect(component.ingressForm.get('tls')).toBeTruthy();
    expect(component.annotationsArray.length).toBeGreaterThan(0);
  });

  it('adds annotations rows on demand', () => {
    const initial = component.annotationsArray.length;
    component.addAnnotation({ key: 'nginx.ingress.kubernetes.io/ssl-redirect', value: 'true' });
    expect(component.annotationsArray.length).toBe(initial + 1);
  });
});
