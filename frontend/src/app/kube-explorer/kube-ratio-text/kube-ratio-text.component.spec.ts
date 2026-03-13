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

import { KubeRatioTextComponent } from './kube-ratio-text.component';

describe('KubeRatioTextComponent', () => {
  let component: KubeRatioTextComponent;
  let fixture: ComponentFixture<KubeRatioTextComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [KubeRatioTextComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(KubeRatioTextComponent);
    component = fixture.componentInstance;
  });

  it('renders a preformatted ratio string as text', () => {
    component.value = '0/1';
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('0/1');
  });

  it('renders numeric numerator/denominator as X/Y text', () => {
    component.numerator = 2;
    component.denominator = 5;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('2/5');
  });

  it('renders fallback when no value or numbers are provided', () => {
    component.fallback = '—';
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('—');
  });

  it('does not render a progress bar element', () => {
    component.value = '1/1';
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('p-progressbar')).toBeNull();
    expect(fixture.nativeElement.querySelector('p-progressBar')).toBeNull();
  });
});

