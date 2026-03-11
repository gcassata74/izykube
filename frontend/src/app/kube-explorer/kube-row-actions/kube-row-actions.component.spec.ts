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
