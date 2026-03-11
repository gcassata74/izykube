import { ClusterService } from 'src/app/services/cluster.service';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Store } from '@ngrx/store';
import { Button, ButtonAction } from '../../model/button.interface';
import { EMPTY, Observable, Subscription, catchError, combineLatest, filter, finalize, map, of, switchMap, take, tap, throwError } from 'rxjs';
import { DiagramComponent } from '../../diagram/diagram.component';
import { ToolbarService } from '../../services/toolbar.service';
import { getCurrentAction, getCurrentCluster } from '../../store/selectors/selectors';
import *  as actions from '../../store/actions/actions';
import { ActivatedRoute } from '@angular/router';
import { Cluster } from 'src/app/model/cluster.class';
import { NotificationService } from 'src/app/services/notification.service';
import { ClusterStatusEnum } from '../enum/cluster.-status-enum';
import { TemplateService } from 'src/app/services/template.service';
import { DiagramService } from 'src/app/services/diagram.service';
import { Link } from 'src/app/model/link.class';
import { ClusterVersion } from 'src/app/model/cluster-version.model';

@Component({
  selector: 'app-cluster-editor',
  templateUrl: './cluster-editor.component.html',
  styleUrls: ['./cluster-editor.component.scss']
})
export class ClusterEditorComponent implements OnInit, OnDestroy {

  @ViewChild('diagram') diagramComponent!: DiagramComponent;
  subscription: Subscription = new Subscription();
  clusterId!: string;
  selectedLink$!: Observable<Link | null>;
  private exportActionsEnabled = false;

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
    this.selectedLink$ = combineLatest([
      this.diagramService.selectedLinkId$,
      this.store.select(getCurrentCluster)
    ]).pipe(
      map(([linkId, cluster]) => {
        if (!linkId) {
          return null;
        }
        const links = cluster?.links || [];
        const match = links.find((link: any) => link.id === linkId);
        if (!match) {
          return null;
        }
        return {
          ...match,
          type: match.type === 'Use' ? 'Use' : 'Expose'
        } as Link;
      })
    );
    this.loadCluster();
    this.setupActionHandlers();
  }


  private loadCluster(): void {
    this.subscription.add(
      combineLatest([this.activatedRoute.params, this.activatedRoute.queryParamMap]).pipe(
        switchMap(([params, queryParams]) => {
          const id = params['id'];
          if (!id) {
            return of(null);
          }
          this.clusterId = id;

          const namespace = queryParams.get('namespace');
          const version = Number(queryParams.get('version'));
          const shouldLoadVersion = !!namespace && Number.isFinite(version) && version > 0;
          if (shouldLoadVersion) {
            return this.clusterService.getNamespaceVersion(namespace!, version).pipe(
              map(snapshot => this.mapVersionToCluster(snapshot))
            );
          }
          return this.clusterService.getCluster(id);
        }),
        tap(cluster => {
          if (!cluster) {
            return;
          }
          this.handleButtonsCreation(cluster);
          this.store.dispatch(actions.loadCluster({ cluster }));
        }),
        catchError(error => {
          this.notificationService.error('Failed to load diagram');
          console.error('Error loading cluster:', error);
          return of(null);
        })
      ).subscribe()
    );
  }

  private mapVersionToCluster(snapshot: ClusterVersion): Cluster {
    return new Cluster(
      snapshot.clusterId || this.clusterId || null,
      snapshot.clusterName || '',
      snapshot.nodes || [],
      snapshot.links || [],
      snapshot.diagram || '',
      snapshot.namespace || 'default',
      ClusterStatusEnum[snapshot.status as keyof typeof ClusterStatusEnum] || ClusterStatusEnum.CREATED
    );
  }


  handleButtonsCreation(cluster: Cluster) {
    this.exportActionsEnabled = cluster.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT
      || cluster.status === ClusterStatusEnum.DEPLOYED;
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
          let handler$ = of(null);
          switch (action) {
            case 'save-diagram':
              handler$ = this.saveCluster();
              break;
            case 'update-template':
              handler$ = this.updateTemplate();
              break;
            case 'update-cluster':
              handler$ = this.updateCluster();
              break;
            case 'import-cluster-yaml':
              this.diagramComponent?.openClusterYamlDialog('import');
              handler$ = of(null);
              break;
            case 'export-cluster-yaml':
              if (!this.exportActionsEnabled) {
                this.notificationService.warn(
                  $localize`:@@clusterEditor.warn.templateRequiredTitle:Template required`,
                  $localize`:@@clusterEditor.warn.templateRequiredDetail:Generate the template before exporting YAML.`
                );
                handler$ = of(null);
                break;
              }
              this.diagramComponent?.openClusterYamlDialog('export');
              handler$ = of(null);
              break;
            case 'open-ai-chat':
              this.diagramComponent?.openChatDialog();
              handler$ = of(null);
              break;
            default:
              handler$ = of(null);
          }
          return handler$.pipe(
            finalize(() => this.store.dispatch(actions.resetCurrentAction()))
          );
        }),
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
          ...(this.exportActionsEnabled ? [{ label: 'Export YAML', action: 'export-cluster-yaml', icon: 'pi pi-download' }] : [])
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
