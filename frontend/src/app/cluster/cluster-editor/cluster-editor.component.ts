import { Deployment } from './../../model/deployment.class';
import { ClusterService } from 'src/app/services/cluster.service';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Store, select } from '@ngrx/store';
import { Button, ButtonAction } from '../../model/button.interface';
import { EMPTY, Observable, Subscription, catchError, filter, finalize, of, switchMap, take, tap, throwError } from 'rxjs';
import { DiagramComponent } from '../../diagram/diagram.component';
import { ToolbarService } from '../../services/toolbar.service';
import { getCurrentAction, getCurrentCluster } from '../../store/selectors/selectors';
import *  as actions from '../../store/actions/actions';
import { ActivatedRoute } from '@angular/router';
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
    protected notificationService: NotificationService,
    private clusterService: ClusterService,
    private templateService: TemplateService,
    private activatedRoute: ActivatedRoute
  ) { }

  ngOnInit(): void {
    this.loadCluster();
    this.setupActionHandlers();
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
                this.notificationService.error('Failed to load diagram');
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

  private setupActionHandlers(): void {
    this.subscription.add(
      this.store.select(getCurrentAction).pipe(
        filter((action): action is string => !!action),
        switchMap(action => {
          switch (action) {
            case 'save-diagram':
              return this.saveCluster();
            case 'update-template':
              return this.updateTemplate();
            case 'update-cluster':
              return this.updateCluster();
            case 'import-cluster-yaml':
              this.diagramComponent?.openClusterYamlDialog('import');
              return of(null);
            case 'export-cluster-yaml':
              this.diagramComponent?.openClusterYamlDialog('export');
              return of(null);
            case 'open-ai-chat':
              this.diagramComponent?.openChatDialog();
              return of(null);
            default:
              return of(null);
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
          this.notificationService.success('Diagram patched successfully');
          this.store.dispatch(actions.resetCurrentAction());
        }),
        catchError(error => {
          this.notificationService.error('Failed to patch diagram');
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
          this.notificationService.success('Namespace template updated successfully');
          this.store.dispatch(actions.resetCurrentAction());
        }),
        catchError(error => {
          this.notificationService.error('Failed to update namespace template');
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
        tap((savedCluster) => {
          if (savedCluster) {
            this.store.dispatch(actions.loadCluster({ cluster: savedCluster }));
          }
          this.notificationService.success('Diagram saved successfully');
          this.store.dispatch(actions.resetCurrentAction());
        }),
        catchError(error => {
          this.notificationService.error('Failed to save diagram');
          console.error('Error saving cluster:', error);
          return throwError(() => error);
        })
      ))
    );
  }

  createButtons(action: string | ButtonAction[]): void {
    const buttons: Button[] = [
      {
        label: 'AI Chat',
        icon: 'pi pi-comments',
        actions: 'open-ai-chat',
        styleClass: 'p-button-secondary'
      },
      {
        label: 'Save',
        icon: 'pi pi-save',
        actions: action,
        styleClass: 'p-button-success'
      },
      {
        label: 'Actions',
        icon: 'pi pi-file-code',
        actions: 'open-actions-menu',
        styleClass: 'p-button-secondary',
        menuItems: [
          { label: 'Import YAML', action: 'import-cluster-yaml', icon: 'pi pi-upload' },
          { label: 'Export YAML', action: 'export-cluster-yaml', icon: 'pi pi-download' }
        ]
      }
    ];

    this.toolbarService.setButtons(buttons);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.toolbarService.clearButtons();
  }
}
