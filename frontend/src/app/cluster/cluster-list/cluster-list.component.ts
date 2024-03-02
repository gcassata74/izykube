import { Component } from '@angular/core';

@Component({
  selector: 'app-cluster-list',
  templateUrl: './cluster-list.component.html',
  styleUrls: ['./cluster-list.component.scss']
})
export class ClusterListComponent {

    clusters = [
      { name: 'testcluster', version: 'A1', created: new Date('2024-01-29T14:00:00'), type: 'TRAINING', owner: 'gcassata', resources: { cpu: 0, ram: 0 }, status: 'CREATING' }
    ];

    addCluster() {
      // Logic to add a new clusterDTO
    }

    refreshTable() {
      // Logic to refresh the table
    }

}
