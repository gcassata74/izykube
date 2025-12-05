import { ToolbarService } from './services/toolbar.service';
import { Component, OnInit, ViewChild } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { Menu } from 'primeng/menu';
import { Store } from '@ngrx/store';
import * as actions from './store/actions/actions';
import { Observable, filter, map } from 'rxjs';
import { Button, ButtonAction, ButtonMenuItem } from './model/button.interface';
import { HeaderContext } from './layout/header/header.component';
import { getCurrentCluster } from './store/selectors/selectors';
import { Cluster } from './model/cluster.class';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {

  title = 'Izykube';
  displaySidebar = true;
  buttons$!: Observable<Button[]>;
  clusterContext$!: Observable<HeaderContext>;
  currentRoute = '/';
  sidebarCollapsed = false;
  @ViewChild('toolbarMenu') toolbarMenu?: Menu;
  menuItems: MenuItem[] = [];

  constructor(
    private router: Router,
    public toolBarService: ToolbarService,
    private store: Store,
  ) {}

  ngOnInit(): void {
    this.buttons$ = this.toolBarService.buttons$;
    this.clusterContext$ = this.store.select(getCurrentCluster).pipe(
      map((cluster: Cluster | undefined) => ({
        clusterName: cluster?.name || 'Untitled Cluster',
        namespace: cluster?.nameSpace || 'default',
        diagramName: cluster?.name || 'Diagram',
      }))
    );
    this.currentRoute = this.router.url || '/';
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event: NavigationEnd) => {
        this.currentRoute = event.urlAfterRedirects || event.url;
      });
  }


  navigate(route: string) {
    this.router.navigate([route]);
  }

  performAction(action: string | ButtonAction[]): void {
    if (typeof action === 'string' && action) {
      this.store.dispatch(actions.setCurrentAction({ action }));
    }
  }

  openActions(event: MouseEvent, items: ButtonMenuItem[] | undefined): void {
    if (!this.toolbarMenu) {
      return;
    }
    this.menuItems = this.buildMenuItems(items);
    this.toolbarMenu.model = this.menuItems;
    this.toolbarMenu.toggle(event);
  }


  buildMenuItems(items: ButtonMenuItem[] | undefined): MenuItem[] {
    if (!items) {
      return [];
    }
    return items.map(item => ({
      label: item.label,
      icon: item.icon,
      command: () => this.performAction(item.action)
    }));
  }

  handleSidebarCollapseChange(collapsed: boolean): void {
    this.sidebarCollapsed = collapsed;
  }

}
