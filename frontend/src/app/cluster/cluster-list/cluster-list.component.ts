import { DataService } from './../../services/data.service';
import { Component, ViewChild } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { ClusterService } from '../../services/cluster.service';
import { Cluster } from '../../model/cluster.class';
import { catchError, combineLatest, EMPTY, filter, find, map, Observable, of, Subscription, switchMap, take, tap, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { ContextMenu } from 'primeng/contextmenu';
import { Store } from '@ngrx/store';
import { getClusters, getStatus } from '../../store/selectors/selectors';
import { loadClusters } from 'src/app/store/actions/actions';
import { ClusterStatusEnum } from '../enum/cluster.-status-enum';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-cluster-list',
  templateUrl: './cluster-list.component.html'
})
export class ClusterListComponent {

  clusters$!: Observable<Cluster[]>;
  @ViewChild('cm') contextMenu!: ContextMenu;
  cols!: any[];
  items!: MenuItem[];
  selectedId!: string;
  private subscriptions = new Subscription();

  constructor(
    private clusterService: ClusterService,
    private dataService: DataService,
    private notificationService: NotificationService,
    private router: Router,
    private store: Store
  ) {}

  ngOnInit() {

    this.getAllClusters();

    this.cols = [
      { field: 'name', header: 'Name' },
      { field: 'nameSpace', header: 'Namespace' },
      { field: 'status', header: 'Status' }
    ];
  }


  private getAllClusters() {
    this.clusters$ = this.clusterService.getAllClusters().pipe(
      tap(clusters => this.store.dispatch(loadClusters({ clusters }))),
      catchError(error => {
        console.error('Error loading clusters:', error);
        return of([]);  // Return an empty array in case of error
      }),
      switchMap(() => this.store.select(getClusters)),
      tap(clusters => console.log('Clusters:', clusters))
    );
  }

  updateContextMenuItems($event: MouseEvent, id: string) {
    this.selectedId = id;
    this.subscriptions.add(
      this.clusters$.pipe(
        map(clusters => clusters.find(cluster => cluster.id === id))
      ).subscribe(
        cluster => {
          if (cluster) {
            this.items = this.generateMenuItems(cluster);
            setTimeout(() =>  { this.contextMenu.show($event); }, 100);
          }
        }
      )
    );
  }

  generateMenuItems(cluster: Cluster): MenuItem[] {
    return [
      { label: 'Edit', icon: 'pi pi-pencil', command: () => cluster.id !== null && this.editCluster(cluster.id) },
      { label: 'Delete', icon: 'pi pi-times', command: () => cluster.id !== null && this.deleteCluster(cluster.id) },
      {
        label: 'Create Template',
        icon: 'pi pi-th-large',
        command: () => cluster.id !== null && this.createTemplate(cluster.id),
        visible: cluster.status === ClusterStatusEnum.CREATED
      },
      {
        label: 'Delete Template',
        icon: 'pi pi-eraser',
        command: () => cluster.id !== null && this.deleteTemplate(cluster.id),
        visible: cluster.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT
      },
      {
        label: 'Deploy',
        icon: 'pi pi-play',
        command: () => cluster.id !== null && this.deploy(cluster.id),
        visible: cluster.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT
      },
      {
        label: 'Undeploy',
        icon: 'pi pi-stop',
        command: () => cluster.id !== null && this.undeploy(cluster.id),
        visible: cluster.status === ClusterStatusEnum.DEPLOYED
      }
    ];
  }

  undeploy(selectedId: string): void {
    this.subscriptions.add(
      this.clusterService.undeploy(selectedId).pipe(
        tap(() => {
          this.notificationService.success('Undeployment Completed', 'Cluster undeployed successfully');
          this.getAllClusters();
        }),
        catchError(error => {
          this.notificationService.error('Undeployment Failed', 'The undeployment could not be completed');
          return EMPTY;
        })
      ).subscribe()
    );
  }

  deploy(selectedId: string): void {
    this.subscriptions.add(
      this.clusterService.deploy(selectedId).pipe(
        tap(() => {
          this.notificationService.success('Deployment Complete', 'The deployment was successful');
          this.getAllClusters();
        }),
        catchError(error => {
          this.notificationService.error('Deployment Failed', 'The deployment could not be completed');
          return EMPTY;
        })
      ).subscribe()
    );
  }

  createTemplate(selectedId: string): void {
    this.subscriptions.add(
      this.clusterService.createTemplate(selectedId).pipe(
        tap((message: any) => {
          this.notificationService.success('Template Created', message.message as string);
          this.getAllClusters();
        }),
        catchError((error: any) => {
          this.notificationService.error('Template Creation Failed', error.error.error);
          return EMPTY;
        })
      ).subscribe()
    );
  }

  deleteTemplate(selectedId: string): void {
    this.subscriptions.add(
      this.clusterService.deleteTemplate(selectedId).pipe(
        tap(() => {
          this.notificationService.success('Template deleted', 'Successfully deleted template');
          this.getAllClusters();
        }
      ),
        catchError(error => {
          this.notificationService.error('Template Deletion Failed', 'The template could not be deleted');
          return EMPTY;
        })
      ).subscribe()
    );
  }

  addCluster() {
    this.router.navigate(['cluster-form']);
  }

  editCluster(id: string) {
    this.router.navigate([`cluster-form/${id}`]);
  }

  deleteCluster(id: string): void {
    this.subscriptions.add(
      this.clusterService.deleteCluster(id).pipe(
        tap(() => {
          this.notificationService.success('Cluster Deleted', 'The cluster was successfully deleted');
          this.getAllClusters();
        }),
        catchError(error => {
          this.notificationService.error('Cluster Deletion Failed', 'The cluster could not be deleted');
          return EMPTY;
        })
      ).subscribe()
    );
  }

  editDiagram(id: string) {
    this.router.navigate([`cluster-editor/${id}`]);
  }

  onContextMenu($event: MouseEvent, id: any) {
    $event.preventDefault();
    this.updateContextMenuItems($event, id);
  }

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
  }
}
