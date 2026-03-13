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

import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

export interface LogOption {
  label: string;
  value: boolean;
}

@Component({
  selector: 'app-log-header',
  templateUrl: './log-header.component.html',
  styleUrls: ['./log-header.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LogHeaderComponent {
  @Input() options: LogOption[] = [];
  @Input() selected: boolean | null = null;
  @Input() loading = false;
  @Input() disabled = false;

  @Output() selectedChange = new EventEmitter<boolean>();
  @Output() reload = new EventEmitter<void>();

  onSelectionChange(value: boolean): void {
    this.selectedChange.emit(value);
  }

  onReload(): void {
    if (this.loading || this.disabled) {
      return;
    }
    this.reload.emit();
  }
}
