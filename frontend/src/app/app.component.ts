import { ToolbarService } from './services/toolbar.service';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { Menu } from 'primeng/menu';
import { Store } from '@ngrx/store';
import * as actions from './store/actions/actions';
import { Observable, Subscription, combineLatest, filter, map, shareReplay, startWith } from 'rxjs';
import { Button, ButtonAction, ButtonMenuItem } from './model/button.interface';
import { HeaderContext } from './layout/header/header.component';
import { getCurrentCluster } from './store/selectors/selectors';
import { Cluster } from './model/cluster.class';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit, OnDestroy {

  title = 'Izykube';
  displaySidebar = true;
  buttons$!: Observable<Button[]>;
  clusterContext$!: Observable<HeaderContext>;
  currentRoute = '/';
  selectedNamespace: string | null = null;
  sidebarCollapsed = false;
  @ViewChild('toolbarMenu') toolbarMenu?: Menu;
  menuItems: MenuItem[] = [];
  private route$!: Observable<string>;
  private routeSub?: Subscription;

  constructor(
    private router: Router,
    public toolBarService: ToolbarService,
    private store: Store,
  ) {}

  ngOnInit(): void {
    this.buttons$ = this.toolBarService.buttons$;
    this.route$ = this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event: NavigationEnd) => event.urlAfterRedirects || event.url),
      startWith(this.router.url || '/'),
      shareReplay({ bufferSize: 1, refCount: true })
    );

    this.clusterContext$ = combineLatest([
      this.store.select(getCurrentCluster),
      this.route$
    ]).pipe(
      map(([cluster, route]: [Cluster | undefined, string]) => {
        const inDiagram = route.includes('cluster-editor');
        return {
          clusterName: null,
          namespace: inDiagram ? (cluster?.nameSpace || cluster?.name || 'default') : null,
          diagramName: null,
          showContext: inDiagram
        };
      })
    );

    this.routeSub = this.route$.subscribe(route => {
      this.currentRoute = route;
      this.selectedNamespace = this.extractNamespaceFromRoute(route);
    });
  }


  navigate(route: string) {
    this.router.navigateByUrl(route);
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

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  private extractNamespaceFromRoute(route: string): string | null {
    const [pathOnly] = route.split('?');
    const match = pathOnly.match(/^\/namespaces\/([^/]+)\/versions/);
    if (!match?.[1]) {
      return null;
    }
    try {
      return decodeURIComponent(match[1]);
    } catch {
      return match[1];
    }
  }

}
