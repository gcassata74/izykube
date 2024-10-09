import { Deployment } from './../../model/deployment.class';
import { ClusterService } from 'src/app/services/cluster.service';
import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Store, select } from '@ngrx/store';
import * as go from 'gojs';
import { Button, ButtonAction } from '../../model/button.interface';
import { EMPTY, Observable, Subscription, catchError, distinctUntilChanged, filter, finalize, first, of, switchMap, take, tap, throwError } from 'rxjs';
import { DiagramComponent } from '../../diagram/diagram.component';
import { DiagramService } from '../../services/diagram.service';
import { ToolbarService } from '../../services/toolbar.service';
import { getCurrentAction, getCurrentCluster } from '../../store/selectors/selectors';
import *  as actions from '../../store/actions/actions';
import { provideAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Params } from '@angular/router';
import { Cluster } from 'src/app/model/cluster.class';
import { NotificationService } from 'src/app/services/notification.service';
import { ClusterStatusEnum } from '../enum/cluster.-status-enum';
import { TemplateService } from 'src/app/services/template.service';

@Component({
  selector: 'app-cluster-editor',
  templateUrl: './cluster-editor.component.html',
  styleUrls: ['./cluster-editor.component.scss']
})
export class ClusterEditorComponent implements OnInit, OnDestroy {

  @ViewChild('diagram') diagramComponent!: DiagramComponent;
  subscription: Subscription = new Subscription();
  clusterId!: string;

  constructor(
    private toolbarService: ToolbarService,
    private store: Store,
    private diagramService: DiagramService,
    protected notificationService: NotificationService,
    private clusterService: ClusterService,
    private templateService: TemplateService,
    private activatedRoute: ActivatedRoute
  ) { }

  ngOnInit(): void {
    this.loadCluster();
    this.setupSaveActions();
  }


  private loadCluster(): void {
    this.subscription.add(
      this.activatedRoute.params.pipe(
        tap(params => {
          const id = params['id'];
          if (id) {
            this.clusterId = id;
            this.clusterService.getCluster(id).pipe(
              tap(cluster => {
                this.handleButtonsCreation(cluster);
                this.store.dispatch(actions.loadCluster({ cluster }))
              }
              ),
              catchError(error => {
                this.notificationService.error('Failed to load cluster');
                console.error('Error loading cluster:', error);
                return of(null);
              })
            ).subscribe();
          }
        })
      ).subscribe()
    );
  }


  handleButtonsCreation(cluster: Cluster) {
    if (cluster.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT) {
      this.createButtons("update-template");
    } else if (cluster.status === ClusterStatusEnum.DEPLOYED) {
      this.createButtons("update-cluster");
    } else {
      this.createButtons("save-diagram");
    }
  }

  private setupSaveActions(): void {
    this.subscription.add(
      this.store.select(getCurrentAction).pipe(
        filter(action => ['save-diagram', 'update-template', 'update-cluster'].includes(action as string)),
        switchMap(action => {
          switch (action) {
            case 'save-diagram':
              return this.saveCluster();
            case 'update-template':
              return this.updateTemplate();
            case 'update-cluster':
              return this.updateCluster();
            default:
              return EMPTY;
          }
        }),
        finalize(() => this.store.dispatch(actions.resetCurrentAction())),
        catchError((error) => {
          console.error('Error in save action:', error);
          this.store.dispatch(actions.resetCurrentAction());
          return EMPTY;
        })
      ).subscribe()
    );
  }

  updateCluster(): any {
    return this.store.select(getCurrentCluster).pipe(
      take(1),
      switchMap(clusterData => this.clusterService.patchCluster(clusterData.id, clusterData).pipe(
        tap(() => {
          this.notificationService.success('Cluster patched successfully');
          this.store.dispatch(actions.resetCurrentAction());
        }),
        catchError(error => {
          this.notificationService.error('Failed to patch cluster');
          console.error('Error saving cluster:', error);
          return throwError(() => error);
        })
      )));
  }

  private updateTemplate(): Observable<any> {
    return this.store.select(getCurrentCluster).pipe(
      take(1),
      switchMap(clusterData => this.templateService.updateTemplate(clusterData).pipe(
        tap(() => {
          this.notificationService.success('Cluster template updated successfully');
          this.store.dispatch(actions.resetCurrentAction());
        }),
        catchError(error => {
          this.notificationService.error('Failed to update template');
          console.error('Error saving cluster:', error);
          return throwError(() => error);
        })
      ))
    );
  }

  private saveCluster(): Observable<any> {
    return this.store.select(getCurrentCluster).pipe(
      take(1),
      switchMap(clusterData => this.clusterService.saveCluster(clusterData).pipe(
        tap(() => {
          this.notificationService.success('Cluster saved successfully');
          this.store.dispatch(actions.resetCurrentAction());
        }),
        catchError(error => {
          this.notificationService.error('Failed to save cluster');
          console.error('Error saving cluster:', error);
          return throwError(() => error);
        })
      ))
    );
  }

  createButtons(action: string | ButtonAction[]): void {
    const button = {
      label: "Save",
      icon: "pi pi-save",
      actions: action,
      styleClass: "p-button-success",
    };

    this.toolbarService.setButtons([button]);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.toolbarService.clearButtons();
  }
}
