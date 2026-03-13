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
import { Button, ButtonAction, ButtonMenuItem } from '../../model/button.interface';

export interface HeaderContext {
  clusterName?: string | null;
  namespace?: string | null;
  diagramName?: string | null;
  showContext?: boolean;
}

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HeaderComponent {
  @Input() buttons: Button[] | null = [];
  @Input() context: HeaderContext | null = null;

  @Output() buttonAction = new EventEmitter<string | ButtonAction[]>();
  @Output() menuTrigger = new EventEmitter<{ event: MouseEvent; items?: ButtonMenuItem[] }>();
  readonly workspaceLabel = $localize`:@@header.workspace:workspace`;
  readonly namespaceLabel = $localize`:@@common.namespaceWithColon:Namespace:`;
  readonly searchAriaLabel = $localize`:@@common.search:Search`;
  readonly searchComingSoonLabel = $localize`:@@header.searchComingSoon:Search (coming soon)`;
  readonly userMenuLabel = $localize`:@@header.userMenu:User menu`;
  readonly profileLabel = $localize`:@@header.profile:Profile`;

  handleButtonClick(button: Button): void {
    this.buttonAction.emit(button.actions);
  }

  handleMenuClick(event: MouseEvent, button: Button): void {
    event.stopPropagation();
    this.menuTrigger.emit({ event, items: button.menuItems });
  }

  getButtonAppearance(styleClass: string): 'primary' | 'secondary' | 'tertiary' {
    if (!styleClass) {
      return 'secondary';
    }
    if (styleClass.includes('success')) {
      return 'primary';
    }
    if (styleClass.includes('secondary')) {
      return 'secondary';
    }
    return 'tertiary';
  }

  trackByLabel(_: number, button: Button): string {
    return button.label;
  }

  buildContextTitle(context: HeaderContext | null): string {
    if (!context || !context.showContext) {
      return '';
    }
    return $localize`:@@header.contextTitle:${this.namespaceLabel}:namespaceLabel: ${context.namespace || '—'}:namespaceValue:`;
  }
}
