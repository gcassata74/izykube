import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { ContextMenu } from 'primeng/contextmenu';
import { MenuItem } from 'primeng/api';
import { catchError, of, Subscription, tap } from 'rxjs';
import { CrdDefinitionSummary } from 'src/app/model/crd-definition.class';
import { CrdService } from 'src/app/services/crd.service';
import { NotificationService } from 'src/app/services/notification.service';

@Component({
  selector: 'app-crd-list',
  templateUrl: './crd-list.component.html',
  styleUrls: ['./crd-list.component.scss']
})
export class CrdListComponent implements OnInit, OnDestroy {
  crds: CrdDefinitionSummary[] = [];
  cols!: any[];
  items!: MenuItem[];
  selectedId!: string;
  @ViewChild('cm') contextMenu!: ContextMenu;

  private subscriptions = new Subscription();

  constructor(
    private crdService: CrdService,
    private notificationService: NotificationService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.cols = [
      { field: 'metadataName', header: 'Name' },
      { field: 'group', header: 'Group' },
      { field: 'version', header: 'Version' },
      { field: 'scope', header: 'Scope' },
      { field: 'kind', header: 'Kind' },
      { field: 'plural', header: 'Plural' },
      { field: 'updatedAt', header: 'Updated' },
    ];

    this.load();
  }

  applyGlobalFilter(table: any, event: Event): void {
    const value = (event.target as HTMLInputElement | null)?.value || '';
    table?.filterGlobal?.(value, 'contains');
  }

  load(): void {
    this.subscriptions.add(
      this.crdService.list().pipe(
        tap(crds => this.crds = crds || []),
        catchError(err => {
          console.error('Error loading CRDs:', err);
          this.notificationService.error('Error', 'Failed to load CRDs');
          return of([]);
        })
      ).subscribe()
    );
  }

  addCrd(): void {
    this.router.navigate(['/crds/new']);
  }

  editCrd(id: string): void {
    this.router.navigate([`/crds/${id}/edit`]);
  }

  deleteCrd(id: string): void {
    this.subscriptions.add(
      this.crdService.delete(id).pipe(
        tap(() => {
          this.notificationService.success('Deleted', 'CRD deleted successfully');
          this.load();
        }),
        catchError(err => {
          console.error('Error deleting CRD:', err);
          this.notificationService.error('Error', 'Failed to delete CRD');
          throw err;
        })
      ).subscribe()
    );
  }

  updateContextMenuItems(event: MouseEvent, id: string): void {
    this.selectedId = id;
    this.items = [
      { label: 'Edit', icon: 'pi pi-pencil', command: () => this.editCrd(id) },
      { label: 'Delete', icon: 'pi pi-times', command: () => this.deleteCrd(id) },
    ];
    setTimeout(() => this.contextMenu.show(event), 100);
  }

  onRowClick(row: CrdDefinitionSummary): void {
    this.editCrd(row.id);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }
}
