import { DataService } from './../../services/data.service';
import { Component } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { ClusterService } from '../../services/cluster.service';
import { Cluster } from 'src/app/model/cluster.class';
import { switchMap } from 'rxjs';
import { Router } from '@angular/router';

@Component({
  selector: 'app-cluster-list',
  templateUrl: './cluster-list.component.html',
  styleUrls: ['./cluster-list.component.scss']
})
export class ClusterListComponent {




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

    this.items = [
      { label: 'Edit', icon: 'pi pi-pencil', command: (event) => this.editCluster(this.selectedId) },
      { label: 'Delete', icon: 'pi pi-times', command: (event) => this.deleteCluster(this.selectedId) }
    ];
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
    this.selectedId = id;
  }

}
