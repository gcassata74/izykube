import { ClusterService } from 'src/app/services/cluster.service';
import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Store, select } from '@ngrx/store';
import * as go from 'gojs';
import { Button } from '../../model/button.interface';
import { EMPTY, Observable, Subscription, catchError, distinctUntilChanged, filter, first, switchMap, take, tap, throwError } from 'rxjs';
import { DiagramComponent } from '../../diagram/diagram.component';
import { DiagramService } from '../../services/diagram.service';
import { ToolbarService } from '../../services/toolbar.service';
import { getCurrentAction, getCurrentCluster } from '../../store/selectors/selectors';
import *  as actions from '../../store/actions/actions';
import { provideAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Params } from '@angular/router';
import { Cluster } from 'src/app/model/cluster.class';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-cluster-editor',
  templateUrl: './cluster-editor.component.html',
  styleUrls: ['./cluster-editor.component.scss']
})
export class ClusterEditorComponent implements OnInit, OnDestroy {

  @ViewChild('diagram') diagramComponent!: DiagramComponent;
  subscription: Subscription = new Subscription();

  constructor(
    private toolbarService: ToolbarService,
    private store: Store,
    private diagramService: DiagramService,
    protected notificationService: NotificationService,
    private clusterService: ClusterService,
    private activatedRoute: ActivatedRoute
  ) { }

  ngOnInit(): void {
    this.createButtons();
    this.loadCluster();
    this.setupSaveAction();
  }

  private loadCluster(): void {
    this.subscription.add(
      this.activatedRoute.params.subscribe(params => {
        const id = params['id'];
        if (id) {
          this.clusterService.getCluster(id).subscribe({
            next: (cluster) => this.store.dispatch(actions.loadCluster({ cluster })),
            error: (error) => {
              this.notificationService.error('Failed to load cluster');
              console.error('Error loading cluster:', error);
            }
          });
        }
      })
    );
  }

  private setupSaveAction(): void {
    this.subscription.add(
      this.store.select(getCurrentAction).subscribe(action => {
        if (action === 'save-diagram') {
          this.saveCluster();
        }
      })
    );
  }

  private saveCluster(): void {
    this.store.select(getCurrentCluster).subscribe(clusterData => {
      this.clusterService.saveCluster(clusterData).subscribe({
        next: () => {
          this.notificationService.success('Cluster saved successfully');
          this.store.dispatch(actions.resetCurrentAction());
        },
        error: (error) => {
          this.notificationService.error('Failed to save cluster');
          console.error('Error saving cluster:', error);
        }
      });
    }).unsubscribe();  
  }

  createButtons() {
    let button: Button = {
      label: "Save",
      icon: "pi pi-save",
      action: "save-diagram",
      styleClass: "p-button-success"
    };

    this.toolbarService.setButtons([button]);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.toolbarService.clearButtons();
  }
}
