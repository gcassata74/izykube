import { Component } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { ClusterService } from '../../services/cluster.service';

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

  constructor(private clusterService: ClusterService) {}

  ngOnInit() {
    this.clusterService.getAllClusters().subscribe(data => {
      this.clusters = data;
    });

    this.cols = [
      { field: 'id', header: 'ID' },
      { field: 'name', header: 'Name' },
      { field: 'namespace', header: 'Namespace' }
    ];

    this.items = [
      { label: 'Edit', icon: 'pi pi-pencil', command: (event) => this.editCluster(this.selectedId) },
      { label: 'Delete', icon: 'pi pi-times', command: (event) => this.deleteCluster(this.selectedId) }
    ];
  }

  addCluster() {
    throw new Error('Method not implemented.');
    }
    

  editCluster(id: string) {
    // Implement the logic to edit the cluster
    console.log(`Edit cluster with id: ${id}`);
  }

  deleteCluster(id: string) {
    // Implement the logic to delete the cluster
    console.log(`Delete cluster with id: ${id}`);
  }

  onContextMenu($event: MouseEvent,id: any) {
      this.selectedId=id;
    }

}
