import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, switchMap } from 'rxjs';
import { ClusterVersion } from 'src/app/model/cluster-version.model';
import { ClusterService } from 'src/app/services/cluster.service';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-namespace-versions',
  templateUrl: './namespace-versions.component.html',
  styleUrls: ['./namespace-versions.component.scss']
})
export class NamespaceVersionsComponent implements OnInit, OnDestroy {
  namespace = '';
  versions: ClusterVersion[] = [];
  loading = false;
  private subscription = new Subscription();

  readonly cols = [
    { field: 'versionNumber', header: $localize`:@@namespaceVersions.col.version:Version` },
    { field: 'clusterName', header: $localize`:@@namespaceVersions.col.diagram:Diagram` },
    { field: 'status', header: $localize`:@@namespaceVersions.col.status:Status` },
    { field: 'createdAt', header: $localize`:@@namespaceVersions.col.createdAt:Saved at` }
  ];

  constructor(
    private route: ActivatedRoute,
    private clusterService: ClusterService,
    private notificationService: NotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      this.route.paramMap.pipe(
        switchMap(params => {
          this.namespace = decodeURIComponent(params.get('namespace') || '');
          this.loading = true;
          return this.clusterService.getNamespaceVersions(this.namespace);
        })
      ).subscribe({
        next: versions => {
          this.versions = versions || [];
          this.loading = false;
        },
        error: (error) => {
          this.versions = [];
          this.loading = false;
          const status = Number(error?.status || 0);
          if (status === 400 || status === 404) {
            return;
          }
          this.notificationService.error(
            $localize`:@@namespaceVersions.error.title:Unable to load versions`,
            $localize`:@@namespaceVersions.error.detail:Namespace versions could not be loaded.`
          );
        }
      })
    );
  }

  openVersion(version: ClusterVersion): void {
    if (!version?.clusterId) {
      return;
    }
    this.router.navigate(
      ['/cluster-editor', version.clusterId],
      { queryParams: { namespace: this.namespace, version: version.versionNumber } }
    );
  }

  formatDate(value: string | undefined): string {
    if (!value) {
      return '-';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleString();
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
