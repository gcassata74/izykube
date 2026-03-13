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

import { Component, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { MenuItem } from 'primeng/api';
import { ContextMenu } from 'primeng/contextmenu';
import { Observable, Subscription, tap, catchError } from 'rxjs';
import { Asset } from 'src/app/model/asset.class';
import { AssetService } from 'src/app/services/asset.service';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-assets-list',
  templateUrl: './assets-list.component.html',
  styleUrls: ['./assets-list.component.scss']
})
  export class AssetListComponent implements OnInit {
    assets$!: Observable<Asset[]>;
    @ViewChild('cm') contextMenu!: ContextMenu;
    cols!: any[];
    items!: MenuItem[];
    selectedId!: string;
    private subscriptions = new Subscription();

    constructor(
      private assetService: AssetService,
      private notificationService: NotificationService,
      private router: Router,
      private store: Store
    ) {}

    ngOnInit() {
      this.getAllAssets();

      this.cols = [
        { field: 'name', header: $localize`:@@common.name:Name` },
        { field: 'type', header: $localize`:@@common.type:Type` },
        { field: 'version', header: $localize`:@@common.version:Version` },
        { field: 'port', header: $localize`:@@common.port:Port` }
      ];
    }

    private getAllAssets() {
      this.assets$ = this.assetService.getAllAssets().pipe(
        tap(assets => console.log('Assets:', assets)),
        catchError(error => {
          console.error('Error loading assets:', error);
          return [];
        })
      );
    }

    updateContextMenuItems($event: MouseEvent, id: string) {
      this.selectedId = id;
      this.items = [
        { label: $localize`:@@common.edit:Edit`, icon: 'pi pi-pencil', command: () => this.editAsset(id) },
        { label: $localize`:@@common.delete:Delete`, icon: 'pi pi-times', command: () => this.deleteAsset(id) }
      ];
      setTimeout(() => { this.contextMenu.show($event); }, 100);
    }

    addAsset() {
      this.router.navigate(['asset-form']);
    }

    editAsset(id: string) {
      this.router.navigate([`asset-form/${id}`]);
    }

    deleteAsset(id: string): void {
      this.subscriptions.add(
        this.assetService.deleteAsset(id).pipe(
          tap(() => {
            this.notificationService.success(
              $localize`:@@asset.deletedTitle:Asset Deleted`,
              $localize`:@@asset.deletedDetail:The asset was successfully deleted`
            );
            this.getAllAssets();
          }),
          catchError(error => {
            this.notificationService.error(
              $localize`:@@asset.deleteFailedTitle:Asset Deletion Failed`,
              $localize`:@@asset.deleteFailedDetail:The asset could not be deleted`
            );
            throw error;
          })
        ).subscribe()
      );
    }

    onContextMenu($event: MouseEvent, id: any) {
      $event.preventDefault();
      this.updateContextMenuItems($event, id);
    }

    ngOnDestroy() {
      this.subscriptions.unsubscribe();
    }
  }
