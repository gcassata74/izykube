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
        { field: 'name', header: 'Name' },
        { field: 'type', header: 'Type' },
        { field: 'version', header: 'Version' },
        { field: 'port', header: 'Port' }
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
        { label: 'Edit', icon: 'pi pi-pencil', command: () => this.editAsset(id) },
        { label: 'Delete', icon: 'pi pi-times', command: () => this.deleteAsset(id) }
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
            this.notificationService.success('Asset Deleted', 'The asset was successfully deleted');
            this.getAllAssets();
          }),
          catchError(error => {
            this.notificationService.error('Asset Deletion Failed', 'The asset could not be deleted');
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
