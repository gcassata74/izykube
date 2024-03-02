import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Store, select } from '@ngrx/store';
import * as go from 'gojs';
import{Button} from '../../model/button.interface';
import { Subscription, distinctUntilChanged, filter, first, switchMap, take, tap } from 'rxjs';
import { DiagramComponent } from '../../diagram/diagram.component';
import { DiagramService } from '../../services/diagram.service';
import { ToolbarService } from '../../services/toolbar.service';
import { getCurrentAction, getClusterData } from '../../store/selectors/selectors';
import * as actions from '../../store/actions/actions';

@Component({
  selector: 'app-cluster-editor',
  templateUrl: './cluster-editor.component.html',
  styleUrls: ['./cluster-editor.component.scss']
})
export class ClusterEditorComponent implements OnInit, OnDestroy{

  model!: go.Model | null;
  @ViewChild('diagram') diagramComponent!: DiagramComponent;
  subscription: Subscription = new Subscription();

 constructor(
  private toolbarService:ToolbarService,
  private store:Store,
  private diagramService: DiagramService
 ){}

  ngOnInit(): void {
    this.createButtons();


      this.subscription.add(
        this.store.pipe(
          select(getCurrentAction),
          distinctUntilChanged(),
          filter(action => action === 'save-diagram'),
          switchMap(() => this.store.select(getClusterData).pipe(take(1)))
        ).subscribe(clusterData => {
          this.diagramService.saveDiagram(clusterData);
          this.store.dispatch(actions.resetCurrentAction());
        })
      );

  }

  createButtons() {
    let button: Button =  {
        label:"Save",
        icon:"pi pi-save",
        action:"save-diagram",
        styleClass:"p-button-success"
    };

    setTimeout(() => {
      this.toolbarService.setButtons([button]);
    });
  }


  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
