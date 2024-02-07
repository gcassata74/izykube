import { DiagramComponent } from './../diagram/diagram.component';
import { ToolbarService } from './../services/toolbar.service';
import {Component, ElementRef, AfterViewInit, OnInit, HostListener, ViewChild} from '@angular/core';
import{Button} from '../model/button.class';
import { Store, select } from '@ngrx/store';
import { getCurrentAction, getClusterData } from '../store/selectors/selectors';
import { filter, first, tap } from 'rxjs';
import * as actions from '../store/actions/actions';
import * as go from 'gojs';
import { Cluster } from '../model/cluster.class';

import { DiagramService } from '../services/diagram.service';
import { ConfigMap, Container, Deployment, Ingress, Pod, Service } from '../model/node.class';
import { addNode, removeNode } from '../store/actions/cluster.actions';


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
  private store:Store,
  private diagramService: DiagramService
 ){}


  ngOnInit() {
    this.createButtons();
      this.store.pipe(select(getCurrentAction)).pipe(
        filter(action => action === 'save-diagram'),
        tap(() => {
          this.saveDiagram();
          setTimeout(() => {
            //reset the current action
            this.store.dispatch(actions.resetCurrentAction());
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
   
   const cluster$ = this.store.select(getClusterData).pipe(
    tap((cluster) => {
      console.log('Cluster', cluster);
    })
   ).subscribe();

   alert('Diagram saved');
  }
}
