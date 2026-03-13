/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, switchMap } from 'rxjs';
import { ConfirmationService } from 'primeng/api';
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
    private router: Router,
    private confirmationService: ConfirmationService
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

  confirmDeleteVersion(version: ClusterVersion): void {
    const versionNumber = Number(version?.versionNumber);
    if (!version?.id || !Number.isFinite(versionNumber)) {
      return;
    }
    this.confirmationService.confirm({
      header: $localize`:@@namespaceVersions.deleteConfirmTitle:Delete version`,
      message: $localize`:@@namespaceVersions.deleteConfirmMessage:Delete version ${versionNumber}:version: from namespace "${this.namespace}:namespace:"?`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: $localize`:@@common.delete:Delete`,
      rejectLabel: $localize`:@@common.cancel:Cancel`,
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteVersion(version.id as string, versionNumber)
    });
  }

  private deleteVersion(versionId: string, versionNumber: number): void {
    this.loading = true;
    this.subscription.add(
      this.clusterService.deleteNamespaceVersionById(versionId).subscribe({
        next: () => {
          this.versions = this.versions.filter(v => v.id !== versionId);
          this.loading = false;
          this.notificationService.success(
            $localize`:@@namespaceVersions.deleteSuccessTitle:Version deleted`,
            $localize`:@@namespaceVersions.deleteSuccessDetail:Version ${versionNumber}:version: removed.`
          );
        },
        error: (error) => {
          this.loading = false;
          const detail = error?.error || $localize`:@@namespaceVersions.deleteErrorDetail:Unable to delete selected version.`;
          this.notificationService.error(
            $localize`:@@namespaceVersions.deleteErrorTitle:Delete failed`,
            typeof detail === 'string' ? detail : undefined
          );
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
