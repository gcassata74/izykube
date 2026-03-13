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
        label: $localize`:@@kubeExplorer.rowActions.viewLogs:View logs`,
        command: () => {
          this.menu?.hide();
          this.viewLogs.emit(this.row);
        },
      },
      {
        label: $localize`:@@kubeExplorer.rowActions.inspectPod:Inspect pod`,
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
