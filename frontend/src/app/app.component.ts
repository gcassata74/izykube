import { ToolbarModule } from 'primeng/toolbar';
import { ToolbarService } from './services/toolbar.service';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { Store } from '@ngrx/store';
import * as actions from './store/actions/actions';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit{

  title = 'frontend';
  displaySidebar = true;


  constructor(
              private router: Router,
              public toolBarService: ToolbarService,
              private store: Store
              ) {}


  ngOnInit(): void {}

  navigate(route: string) {
    this.router.navigate([route]);
  }

  performAction(action: string) {
    this.store.dispatch(actions.setCurrentAction({ action }));
  }

}
