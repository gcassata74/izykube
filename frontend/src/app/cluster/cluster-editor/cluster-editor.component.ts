import { ClusterService } from 'src/app/services/cluster.service';
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
import *  as clusterActions from '../../store/actions/cluster.actions';
import { provideAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute } from '@angular/router';

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
  private diagramService: DiagramService,
  private clusterService: ClusterService,
  private activatedRoute: ActivatedRoute
 ){}

  ngOnInit(): void {
    this.createButtons();

    this.subscription.add(
      this.activatedRoute.params.subscribe(params => {
        const id = params['id'];
        if (id) {
          this.loadCluster(id);
        }
      }));

      this.subscription.add(
        this.store.pipe(
          select(getCurrentAction),
          distinctUntilChanged(),
          filter(action => action === 'save-diagram'),
          switchMap(() => this.store.select(getClusterData).pipe(take(1)))
        ).subscribe(clusterData => {
          this.clusterService.saveCluster(clusterData);
          this.store.dispatch(actions.resetCurrentAction());
        })
      );
  }

  loadCluster(clusterId: any) {
    this.store.dispatch(clusterActions.loadCluster({id:clusterId}));

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
