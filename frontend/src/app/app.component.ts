import { ToolbarModule } from 'primeng/toolbar';
import { ToolbarService } from './services/toolbar.service';
import { AfterViewInit, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MenuItem, MenuItemCommandEvent } from 'primeng/api';
import { Store } from '@ngrx/store';
import * as actions from './store/actions/actions';
import { Observable, filter, map, of, startWith, switchMap, tap } from 'rxjs';
import { Button, ButtonAction } from './model/button.interface';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {

  title = 'Izykube';
  displaySidebar = true;
  Array: any;
  action: any;
  items: MenuItem[] = [];
  buttons$!: Observable<Button[]>;

  constructor(
    private router: Router,
    public toolBarService: ToolbarService,
    private store: Store,
  ) {}

  ngOnInit(): void {
    this.buttons$ = this.toolBarService.buttons$.pipe(
      tap(buttons => {
        this.items = this.convertActionsToMenuItems(buttons.flatMap(button => Array.isArray(button.actions) ? button.actions : []));
      })
    );
  }

  convertActionsToMenuItems(actions: string | ButtonAction[]): any {
    if (this.isArray(actions)) {
      return (actions as ButtonAction[]).splice(1).map(action => {
        return {
          label: action.label,
          command: (event: MenuItemCommandEvent) => this.performAction(action.action),
        };
      });
    }
  }


  navigate(route: string) {
    this.router.navigate([route]);
  }

  performAction(action: string | ButtonAction[]): void {
    if(typeof action === 'string') {
      this.store.dispatch(actions.setCurrentAction({ action }));
    }
  }

  handleSplitButtonClick(actions: string | ButtonAction[]): void {
    if (Array.isArray(actions) && actions.length > 0) {
      this.performAction(actions[0].action);
    }
  }


  isArray(action: string | ButtonAction[]): any {
    return Array.isArray(action);
  }

}
