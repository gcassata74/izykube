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

import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';

import { KubeRowActionsComponent, KubeRowRef } from './kube-row-actions.component';

@Component({
  template: `
    <app-kube-row-actions
      [row]="row"
      (viewLogs)="onViewLogs($event)"
      (inspectPod)="onInspectPod($event)"
    ></app-kube-row-actions>
  `,
})
class HostComponent {
  row: KubeRowRef = { name: 'mypod', namespace: 'default' };
  viewLogsCalls: KubeRowRef[] = [];
  inspectCalls: KubeRowRef[] = [];

  onViewLogs(row: KubeRowRef): void {
    this.viewLogsCalls.push(row);
  }

  onInspectPod(row: KubeRowRef): void {
    this.inspectCalls.push(row);
  }
}

describe('KubeRowActionsComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  afterEach(() => {
    document.body.querySelectorAll('.p-menu-overlay').forEach(node => node.remove());
    fixture?.destroy();
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, ButtonModule, MenuModule],
      declarations: [HostComponent, KubeRowActionsComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('opens the menu and shows both items', () => {
    const button = fixture.debugElement.query(By.css('button[aria-label="Row actions"]'));
    button.nativeElement.click();
    fixture.detectChanges();

    const menuText = document.body.textContent || '';
    expect(menuText).toContain('View logs');
    expect(menuText).toContain('Inspect pod');
  });

  it('emits viewLogs when selecting "View logs"', () => {
    const rowActions = fixture.debugElement.query(By.directive(KubeRowActionsComponent)).componentInstance as KubeRowActionsComponent;

    rowActions.items[0].command?.({} as any);
    fixture.detectChanges();

    expect(fixture.componentInstance.viewLogsCalls).toEqual([{ name: 'mypod', namespace: 'default' }]);
  });

  it('emits inspectPod when selecting "Inspect pod"', () => {
    const rowActions = fixture.debugElement.query(By.directive(KubeRowActionsComponent)).componentInstance as KubeRowActionsComponent;
    rowActions.items[1].command?.({} as any);
    fixture.detectChanges();

    expect(fixture.componentInstance.inspectCalls).toEqual([{ name: 'mypod', namespace: 'default' }]);
  });
});
