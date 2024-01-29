import { ToolbarService } from './../services/toolbar.service';
import {Component, ElementRef, AfterViewInit, OnInit, HostListener, ViewChild} from '@angular/core';
import{Button} from '../model/button.class';
import { Store, select } from '@ngrx/store';
import { getCurrentAction } from '../store/selectors/selectors';
import { filter, first, tap } from 'rxjs';
import * as actions from '../store/actions/actions';


@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {

 constructor(
  private toolbarService:ToolbarService,
  private store:Store
 ){}

  ngOnInit() {
    this.createButtons();
    this.store.pipe(select(getCurrentAction)).pipe(
      filter(action => action === 'save-diagram'),
      tap(()=>this.saveDiagram()),
      first()
      ).subscribe();
  }

  createButtons() {
    let button: Button =  {
        label:"Save",
        icon:"pi pi-save",
        action:"save-diagram",
        styleClass:"p-button-success"
    };
    this.toolbarService.setButtons([button]);
  }

  saveDiagram(): void {
   alert('Diagram saved');
   // reset the current action
   this.store.dispatch(actions.setCurrentAction({action: ''}));
  }
}
