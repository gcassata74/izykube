import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { Menu } from 'primeng/menu';

export interface KubeRowRef {
  name: string;
  namespace: string;
}

@Component({
  selector: 'app-kube-row-actions',
  templateUrl: './kube-row-actions.component.html',
  styleUrls: ['./kube-row-actions.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KubeRowActionsComponent {
  @Input({ required: true }) row!: KubeRowRef;

  @Output() viewLogs = new EventEmitter<KubeRowRef>();
  @Output() inspectPod = new EventEmitter<KubeRowRef>();

  @ViewChild('menu') menu?: Menu;

  get items(): MenuItem[] {
    return [
      {
        label: 'View logs',
        command: () => {
          this.menu?.hide();
          this.viewLogs.emit(this.row);
        },
      },
      {
        label: 'Inspect pod',
        command: () => {
          this.menu?.hide();
          this.inspectPod.emit(this.row);
        },
      },
    ];
  }

  toggle(event: Event): void {
    event.stopPropagation();
    this.menu?.toggle(event);
  }
}
