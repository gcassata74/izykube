import { Subscription } from 'rxjs';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { Cluster } from '../../model/cluster.class';
import { ClusterService } from '../../services/cluster.service';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-cluster-form',
  templateUrl: './cluster-form.component.html',
  styleUrls: ['./cluster-form.component.scss']
})
export class ClusterFormComponent implements OnInit, OnDestroy{

  clusterForm!: FormGroup;
  isEditMode: boolean = false;
  clusterId: string | null = null;
  cluster!: Cluster;
  subscription: Subscription = new Subscription();

  constructor(
    private formBuilder: FormBuilder,
    private clusterService: ClusterService,
    private router: Router,
    private route: ActivatedRoute,
    private notificationService: NotificationService
  ) {

  }

  ngOnInit(): void {


    this.clusterForm = this.formBuilder.group({
      name: ['', Validators.required],
      namespace: ['default', Validators.required]
    });

    this.route.paramMap.subscribe(params => {
      this.clusterId = params.get('id');
      this.isEditMode = !!this.clusterId;

      if (this.isEditMode && this.clusterId) {
        this.loadClusterData(this.clusterId);
      }
    });
  }

  loadClusterData(id: string) {
    this.subscription.add(this.clusterService.getCluster(id).subscribe(data => {
      this.cluster = data;
      this.clusterForm.patchValue({
        name: this.cluster.name,
        namespace: this.cluster.nameSpace
      });
    }));
  }

  saveCluster() {
    const values = this.clusterForm.value;
    if (!this.isEditMode) {
      this.cluster = new Cluster();
    }
    this.cluster.name = values.name;
    this.cluster.nameSpace = values.namespace;
    this.clusterService.saveCluster(this.cluster).subscribe(()=>{
      this.notificationService.success('Success', 'Cluster creared successfully');
      this.router.navigate(['/clusters']);
    })
  }

  ngOnDestroy(): void {
   this.subscription.unsubscribe();
  }

  cancel() {
    this.router.navigate(['/clusters']);
  }


}
