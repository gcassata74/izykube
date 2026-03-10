import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

interface SidebarNavItem {
  label: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-sidebar',
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SidebarComponent {
  @Input() collapsed = false;
  @Input() activeRoute: string | null = null;

  @Output() navigate = new EventEmitter<string>();
  @Output() collapseChange = new EventEmitter<boolean>();

  navItems: SidebarNavItem[] = [
    { label: $localize`:@@sidebar.home:Home`, icon: 'pi pi-home', route: '/home' },
    { label: $localize`:@@sidebar.operators:Operators`, icon: 'pi pi-box', route: '/operators' },
    { label: $localize`:@@sidebar.namespaces:Namespaces`, icon: 'pi pi-th-large', route: '/namespaces' },
    { label: $localize`:@@sidebar.assets:Assets`, icon: 'pi pi-briefcase', route: '/assets' },
    { label: $localize`:@@sidebar.kubeExplorer:Kube Explorer`, icon: 'pi pi-compass', route: '/kube-explorer' },
    { label: $localize`:@@sidebar.routes:Routes`, icon: 'pi pi-globe', route: '/routes' },
  ];

  readonly settingsLabel = $localize`:@@common.settings:Settings`;
  readonly helpLabel = $localize`:@@common.help:Help`;
  readonly helpDocsLabel = $localize`:@@sidebar.helpDocs:Help & Docs`;
  readonly expandSidebarLabel = $localize`:@@sidebar.expandSidebar:Expand sidebar`;
  readonly collapseSidebarLabel = $localize`:@@sidebar.collapseSidebar:Collapse sidebar`;
  readonly expandLabel = $localize`:@@common.expand:Expand`;
  readonly collapseLabel = $localize`:@@common.collapse:Collapse`;

  onNavigate(route: string): void {
    this.navigate.emit(route);
  }

  toggleCollapse(): void {
    this.collapseChange.emit(!this.collapsed);
  }

  isActive(route: string): boolean {
    if (!this.activeRoute) {
      return false;
    }
    if (route === '/home') {
      return this.activeRoute === '/' || this.activeRoute.startsWith('/home');
    }
    return this.activeRoute.startsWith(route);
  }
}
