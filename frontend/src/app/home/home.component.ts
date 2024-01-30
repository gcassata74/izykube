import { DiagramComponent } from './../diagram/diagram.component';
import { ToolbarService } from './../services/toolbar.service';
import {Component, ElementRef, AfterViewInit, OnInit, HostListener, ViewChild} from '@angular/core';
import{Button} from '../model/button.class';
import { Store, select } from '@ngrx/store';
import { getCurrentAction } from '../store/selectors/selectors';
import { filter, first, tap } from 'rxjs';
import * as actions from '../store/actions/actions';
import * as go from 'gojs';


@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit,AfterViewInit {

  model!: go.Model | null;
  @ViewChild('diagram') diagramComponent!: DiagramComponent;

 constructor(
  private toolbarService:ToolbarService,
  private store:Store
 ){}


  ngOnInit() {
    this.createButtons();
      this.store.pipe(select(getCurrentAction)).pipe(
        filter(action => action === 'save-diagram'),
        tap(() => {
          this.saveDiagram();
          setTimeout(() => {
            //reset the current action
            this.store.dispatch(actions.setCurrentAction({ action: 'none' }));
          }, 1000);
        }),
      ).subscribe();
  }

  ngAfterViewInit(): void {
    this.diagramComponent.diagram.addDiagramListener('ChangedSelection', (e) => {
      const node = e.diagram.selection.first();
      if (node instanceof go.Node) {
        this.showAssetSelectionForm(node);
      }
    });
  }


  showAssetSelectionForm(node: go.Node) {
     


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
   
    this.model = this.diagramComponent.diagram.model;
    this.model.nodeDataArray[0];

   alert('Diagram saved');
  }
}
