import { NotificationService } from 'src/app/services/notification.service';
import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { Cluster } from '../model/cluster.class';
import { DataService } from './data.service';
import { Injectable } from '@angular/core';
import { clusterDeployed, clusterUndeployed, templateCreated, templateDeleted, updateCluster } from '../store/actions/cluster.actions';
import { Store } from '@ngrx/store';
import { Router } from '@angular/router';

@Injectable()
export class ClusterService {


  constructor(
    private notificationService: NotificationService,
    private dataService: DataService,
    private store: Store,
    private router: Router
    ) { }


  getAllClusters(): Observable<Cluster[]> {
    return this.dataService.get<Cluster[]>('/cluster/all');
  }

  getCluster(id: string): Observable<Cluster> {
    return this.dataService.get<Cluster>('/cluster/'+id);
  }


  deleteCluster(id: string): Observable<Cluster[]> {
    return this.dataService.delete<Cluster>('/cluster/'+id).pipe(
      switchMap(() => this.getAllClusters())
    )
  }


  saveCluster(clusterData: Cluster) {
    let saveObservable: Observable<Cluster>;

    if (clusterData.id) {
      // If the ID is present, use PUT to update
      saveObservable = this.dataService.put<Cluster>('cluster/' + clusterData.id, clusterData);
    } else {
      // If the ID is not present, use POST to create
      saveObservable = this.dataService.post<Cluster>('cluster', clusterData);
    }
    saveObservable.pipe(
      catchError((error) => {
        this.notificationService.error('Save Failed', 'The cluster could not be saved');
        return throwError(() => error);
      })
    ).subscribe((savedCluster:Cluster) => {
      // Dispatch the update action with the saved cluster data
      this.store.dispatch(updateCluster({ cluster: savedCluster }));
      this.notificationService.success('Cluster Saved', 'The cluster was saved successfully');
       this.router.navigate(['/clusters']);
    });
  }

  createTemplate(selectedId: string) {
    this.dataService.post('/cluster/'+selectedId+'/template', {}).pipe(
      catchError((error: any) => {
        this.notificationService.error('Template Creation Failed', error.error.error);
        return throwError(() => error);
      })
    ).subscribe((message: any) => {
      this.store.dispatch(templateCreated());
      this.notificationService.success('Template Created', message.message as string);
    });
  }

  deleteTemplate(selectedId: string) {
    this.dataService.delete('/cluster/'+selectedId+'/template',).pipe(
      catchError((error) => {
        this.notificationService.error('template deletion Failed', 'template could not be deleted');
        return throwError(() => error);
      })
    ).subscribe(() => {
      this.store.dispatch(templateDeleted());
      this.notificationService.success('Template deleted', 'template deleted successfully');
    });
  }

  deploy(selectedId: string) {
   this.dataService.post('/cluster/'+selectedId+'/deploy', {}).pipe(
      catchError((error) => {
        this.notificationService.error('Deployment Failed', 'The deployment could not be completed');
        return throwError(() => error);
      })
    ).subscribe(() => {
      this.store.dispatch(clusterDeployed());
      this.notificationService.success('Deployment Complete', 'The deployment was successful');
    });
  }

  undeploy(selectedId: string) {
    this.dataService.delete('/cluster/'+selectedId+'/undeploy').pipe(
      catchError((error) => {
        this.notificationService.error('Undeployment Failed', 'The undeployment could not be completed');
        return throwError(() => error);
      })
    ).subscribe(() => {
      this.store.dispatch(clusterUndeployed());
      this.notificationService.success('Undeployment Completed', 'cluster undeployed successfully');
    });
  }

}
