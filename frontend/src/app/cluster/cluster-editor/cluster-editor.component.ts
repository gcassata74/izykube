import { Component, OnInit, ViewChild } from '@angular/core';
import { Store, select } from '@ngrx/store';
import * as go from 'gojs';
import{Button} from '../../model/button.class';
import { filter, tap } from 'rxjs';
import { DiagramComponent } from 'src/app/diagram/diagram.component';
import { DiagramService } from 'src/app/services/diagram.service';
import { ToolbarService } from 'src/app/services/toolbar.service';
import { getCurrentAction, getClusterData } from 'src/app/store/selectors/selectors';
import * as actions from '../../store/actions/actions';

@Component({
  selector: 'app-cluster-editor',
  templateUrl: './cluster-editor.component.html',
  styleUrls: ['./cluster-editor.component.scss']
})
export class ClusterEditorComponent implements OnInit{

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
            this.store.dispatch(actions.resetCurrentAction());
        }),
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
   
   const cluster$ = this.store.select(getClusterData).pipe(
    tap((cluster) => {
      console.log('Cluster', cluster);
    })
   ).subscribe();

   alert('Diagram saved');
  }
}
