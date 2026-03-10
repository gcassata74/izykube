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
