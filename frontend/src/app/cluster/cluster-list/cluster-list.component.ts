import { DataService } from './../../services/data.service';
import { Component, ViewChild } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { ClusterService } from '../../services/cluster.service';
import { Cluster } from 'src/app/model/cluster.class';
import { switchMap } from 'rxjs';
import { Router } from '@angular/router';
import { ContextMenu } from 'primeng/contextmenu';

@Component({
  selector: 'app-cluster-list',
  templateUrl: './cluster-list.component.html',
  styleUrls: ['./cluster-list.component.scss']
})
export class ClusterListComponent {

  @ViewChild('cm') contextMenu!: ContextMenu; 
  clusters: any[] = [];
  cols!: any[];
  items!: MenuItem[];
  selectedId!: string;

  constructor(
    private clusterService: ClusterService,
    private dataService: DataService,
    private router: Router
  ) { }

  ngOnInit() {
    this.clusterService.getAllClusters().subscribe(data => {
      this.clusters = data;
    });

    this.cols = [
      // { field: 'id', header: 'ID' },
      { field: 'name', header: 'Name' },
      { field: 'nameSpace', header: 'Namespace' }
    ];
  }

  updateContextMenuItems($event: MouseEvent, id: string) {
    this.selectedId = id; // Set selectedId to the right-clicked row's ID
    this.items = [
      { label: 'Edit', icon: 'pi pi-pencil', command: () => this.editCluster(this.selectedId) },
      { label: 'Delete', icon: 'pi pi-times', command: () => this.deleteCluster(this.selectedId) },
      { label: 'Create Template', icon: 'pi pi-th-large', command: () => this.createTemplate(this.selectedId) },
      { label: 'Deploy', icon: 'pi pi-play', command: () => this.deploy(this.selectedId) }
    ];
    this.contextMenu.show($event);
  }

  deploy(selectedId: string): void {
    this.clusterService.deploy(selectedId);
  }

  createTemplate(selectedId: string): void {
   this.clusterService.createTemplate(selectedId);
  }

  addCluster() {
    this.router.navigate(['cluster-form']);
  }

  editCluster(id: string) {
    this.router.navigate([`cluster-form/${id}`]);
  }

  deleteCluster(id: string): void {
    this.clusterService.deleteCluster(id).subscribe(data => {
      this.clusters = data;
    });
  }

  editDiagram(id: string) {
    this.router.navigate([`cluster-editor/${id}`]);
  }

  onContextMenu($event: MouseEvent, id: any) {
    $event.preventDefault();
    this.updateContextMenuItems($event,id);
  }


}
