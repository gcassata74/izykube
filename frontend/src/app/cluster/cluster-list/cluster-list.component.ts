import { DataService } from './../../services/data.service';
import { Component, ViewChild } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { ClusterService } from '../../services/cluster.service';
import { Cluster } from '../../model/cluster.class';
import { catchError, combineLatest, EMPTY, filter, find, map, Observable, of, switchMap, take, tap } from 'rxjs';
import { Router } from '@angular/router';
import { ContextMenu } from 'primeng/contextmenu';
import { Store } from '@ngrx/store';
import { getClusters, getStatus } from '../../store/selectors/selectors';
import { loadClusters } from 'src/app/store/actions/actions';
import { ClusterStatusEnum } from '../enum/cluster.-status-enum';

@Component({
  selector: 'app-cluster-list',
  templateUrl: './cluster-list.component.html',
  styleUrls: ['./cluster-list.component.scss']
})
export class ClusterListComponent {


  clusters$!: Observable<Cluster[]>;

  @ViewChild('cm') contextMenu!: ContextMenu;
  cols!: any[];
  items!: MenuItem[];
  selectedId!: string;

  constructor(
    private clusterService: ClusterService,
    private dataService: DataService,
    private router: Router,
    private store: Store
  ) {
  }

  ngOnInit() {


    this.clusters$ = this.store.select(getClusters);

    this.getAllClusters();

    this.cols = [
      // { field: 'id', header: 'ID' },
      { field: 'name', header: 'Name' },
      { field: 'nameSpace', header: 'Namespace' }
    ];
  }


  getAllClusters() {
    this.clusterService.getAllClusters().pipe(
      tap(clusters => this.store.dispatch(loadClusters({ clusters }))),
      catchError(error => {
        console.error('Error loading clusters:', error);
        return EMPTY;
      })
    ).subscribe();
  
  }

  updateContextMenuItems($event: MouseEvent, id: string) {
    this.selectedId = id;
    this.clusters$.pipe(
      map(clusters => clusters.find(cluster => cluster.id === id))
    ).subscribe(
      cluster => {
        if (cluster) {
          this.items = this.generateMenuItems(cluster);
          this.contextMenu.show($event);
        }
      }
    );
  }

  generateMenuItems(cluster: Cluster): MenuItem[] {
    return [
      { label: 'Edit', icon: 'pi pi-pencil', command: () => cluster.id!==null && this.editCluster(cluster.id) },
      { label: 'Delete', icon: 'pi pi-times', command: () => cluster.id!==null && this.deleteCluster(cluster.id) },
      {
        label: 'Create Template',
        icon: 'pi pi-th-large',
        command: () => cluster.id!==null && this.createTemplate(cluster.id),
        visible: cluster.status === ClusterStatusEnum.CREATED
      },
      {
        label: 'Delete Template',
        icon: 'pi pi-eraser',
        command: () => cluster.id!==null && this.deleteTemplate(cluster.id),
        visible: cluster.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT
      },
      {
        label: 'Deploy',
        icon: 'pi pi-play',
        command: () => cluster.id!==null && this.deploy(cluster.id),
        visible: cluster.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT
      },
      {
        label: 'Undeploy',
        icon: 'pi pi-stop',
        command: () => cluster.id!==null && this.undeploy(cluster.id),
        visible: cluster.status === ClusterStatusEnum.DEPLOYED
      }
    ];
  }



  undeploy(selectedId: string): void {
    this.clusterService.undeploy(selectedId);
    this.getAllClusters();
  }

  deploy(selectedId: string): void {
    this.clusterService.deploy(selectedId);
    this.getAllClusters();
  }

  createTemplate(selectedId: string): void {
    this.clusterService.createTemplate(selectedId);
    this.getAllClusters();
  }

  deleteTemplate(selectedId: string): void {
    this.clusterService.deleteTemplate(selectedId);
    this.getAllClusters();
  }


  addCluster() {
    this.router.navigate(['cluster-form']);
  }

  editCluster(id: string) {
    this.router.navigate([`cluster-form/${id}`]);
  }

  deleteCluster(id: string): void {
    this.clusterService.deleteCluster(id).subscribe(clusters => {
      this.store.dispatch(loadClusters({ clusters }));
    });
  }

  editDiagram(id: string) {
    this.router.navigate([`cluster-editor/${id}`]);
  }

  onContextMenu($event: MouseEvent, id: any) {
    $event.preventDefault();
    this.updateContextMenuItems($event, id);
  }


}
