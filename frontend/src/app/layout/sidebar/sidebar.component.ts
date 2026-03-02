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
    { label: 'Home', icon: 'pi pi-home', route: '/home' },
    { label: 'Namespaces', icon: 'pi pi-th-large', route: '/namespaces' },
    { label: 'Assets', icon: 'pi pi-briefcase', route: '/assets' },
    { label: 'CRDs', icon: 'pi pi-table', route: '/crds' },
    { label: 'Kube Explorer', icon: 'pi pi-compass', route: '/kube-explorer' },
    { label: 'Routes', icon: 'pi pi-globe', route: '/routes' },
  ];

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
