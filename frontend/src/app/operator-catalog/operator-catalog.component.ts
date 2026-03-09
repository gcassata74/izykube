import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription, catchError, finalize, of, tap } from 'rxjs';
import {
  ManagedCrdRef,
  OperatorCatalogEntry,
  OperatorCatalogPayload,
  OperatorUninstallPolicy,
} from '../model/operator-catalog.model';
import { OperatorHubOperator } from '../model/operator-hub.model';
import { NotificationService } from '../services/notification.service';
import { OperatorCatalogService } from '../services/operator-catalog.service';
import { OperatorHubService } from '../services/operator-hub.service';
import { PaginatorState } from 'primeng/paginator';

interface CatalogFormModel {
  id?: string;
  name: string;
  packageName: string;
  channel: string;
  targetNamespace: string;
  desiredVersion: string;
  uninstallPolicy: OperatorUninstallPolicy;
  manifestYaml: string;
  managedCrdsRaw: string;
}

@Component({
  selector: 'app-operator-catalog',
  templateUrl: './operator-catalog.component.html',
  styleUrls: ['./operator-catalog.component.scss'],
})
export class OperatorCatalogComponent implements OnInit, OnDestroy {
  entries: OperatorCatalogEntry[] = [];
  loading = false;
  dialogVisible = false;
  saving = false;
  hubOperators: OperatorHubOperator[] = [];
  hubLoading = false;
  hubQuery = '';
  hubPage = 1; // 1-based for backend
  hubSize = 24;
  hubTotal = 0;

  uninstallPolicies = [
    { label: $localize`:@@operatorCatalog.policy.retainCrds:Retain CRDs`, value: 'RETAIN_CRDS' as OperatorUninstallPolicy },
    { label: $localize`:@@operatorCatalog.policy.deleteCrdsIfEmpty:Delete CRDs If Empty`, value: 'DELETE_CRDS_IF_EMPTY' as OperatorUninstallPolicy },
    { label: $localize`:@@operatorCatalog.policy.forceDelete:Force Delete`, value: 'FORCE_DELETE' as OperatorUninstallPolicy },
  ];

  form: CatalogFormModel = this.emptyForm();

  private subscriptions = new Subscription();

  constructor(
    private operatorCatalogService: OperatorCatalogService,
    private notificationService: NotificationService,
    private operatorHubService: OperatorHubService,
  ) {}

  ngOnInit(): void {
    this.load();
    this.loadHubOperators(true);
  }

  load(): void {
    this.loading = true;
    this.subscriptions.add(
      this.operatorCatalogService
        .list()
        .pipe(
          tap((entries) => (this.entries = entries || [])),
          catchError((err) => {
            console.error('Error loading operator catalog:', err);
            this.notificationService.error(
              $localize`:@@common.error:Error`,
              $localize`:@@operatorCatalog.error.load:Failed to load operator catalog`,
            );
            return of([]);
          }),
          finalize(() => (this.loading = false)),
        )
        .subscribe(),
    );
  }

  openCreate(): void {
    this.form = this.emptyForm();
    this.dialogVisible = true;
  }

  importFromHub(operator: OperatorHubOperator): void {
    const installKey = (operator.installKey || operator.name || '').trim();
    if (!installKey) {
      return;
    }
    this.subscriptions.add(
      this.operatorHubService
        .fetchInstallYaml(installKey)
        .pipe(
          tap((yaml) => {
            this.form = {
              ...this.emptyForm(),
              name: operator.name,
              packageName: installKey,
              manifestYaml: yaml || '',
            };
            this.dialogVisible = true;
          }),
          catchError((err) => {
            this.notificationService.error(
              $localize`:@@operatorHub.error.install:Failed to fetch install YAML`,
              this.extractError(err, $localize`:@@operatorHub.error.install:Failed to fetch install YAML`),
            );
            return of(null);
          }),
        )
        .subscribe(),
    );
  }

  openEdit(entry: OperatorCatalogEntry): void {
    this.form = {
      id: entry.id,
      name: entry.name,
      packageName: entry.packageName,
      channel: entry.channel || '',
      targetNamespace: entry.targetNamespace,
      desiredVersion: entry.desiredVersion,
      uninstallPolicy: entry.uninstallPolicy || 'RETAIN_CRDS',
      manifestYaml: entry.manifestYaml || '',
      managedCrdsRaw: this.stringifyManagedCrds(entry.managedCrds),
    };
    this.dialogVisible = true;
  }

  save(): void {
    const payload = this.toPayload(this.form);
    this.saving = true;

    const request$ = this.form.id
      ? this.operatorCatalogService.update(this.form.id, payload)
      : this.operatorCatalogService.create(payload);

    this.subscriptions.add(
      request$
        .pipe(
          tap(() => {
            this.notificationService.success(
              $localize`:@@common.saved:Saved`,
              $localize`:@@operatorCatalog.saved:Catalog entry saved successfully`,
            );
            this.dialogVisible = false;
            this.load();
          }),
          catchError((err) => {
            console.error('Error saving catalog entry:', err);
            this.notificationService.error(
              $localize`:@@common.error:Error`,
              this.extractError(err, $localize`:@@operatorCatalog.error.save:Failed to save catalog entry`),
            );
            return of(null);
          }),
          finalize(() => (this.saving = false)),
        )
        .subscribe(),
    );
  }

  install(entry: OperatorCatalogEntry): void {
    this.subscriptions.add(
      this.operatorCatalogService
        .install(entry.id)
        .pipe(
          tap(() => {
            this.notificationService.success(
              $localize`:@@operatorCatalog.action.installed:Installed`,
              $localize`:@@operatorCatalog.action.installedDetail:${entry.name} installed`,
            );
            this.load();
          }),
          catchError((err) => {
            this.notificationService.error(
              $localize`:@@operatorCatalog.action.installFailed:Install failed`,
              this.extractError(err, $localize`:@@operatorCatalog.action.installFailed:Install failed`),
            );
            return of(null);
          }),
        )
        .subscribe(),
    );
  }

  upgrade(entry: OperatorCatalogEntry): void {
    this.subscriptions.add(
      this.operatorCatalogService
        .upgrade(entry.id)
        .pipe(
          tap(() => {
            this.notificationService.success(
              $localize`:@@operatorCatalog.action.updated:Updated`,
              $localize`:@@operatorCatalog.action.updatedDetail:${entry.name} updated`,
            );
            this.load();
          }),
          catchError((err) => {
            this.notificationService.error(
              $localize`:@@operatorCatalog.action.updateFailed:Update failed`,
              this.extractError(err, $localize`:@@operatorCatalog.action.updateFailed:Update failed`),
            );
            return of(null);
          }),
        )
        .subscribe(),
    );
  }

  uninstall(entry: OperatorCatalogEntry, force = false): void {
    this.subscriptions.add(
      this.operatorCatalogService
        .uninstall(entry.id, { force })
        .pipe(
          tap(() => {
            this.notificationService.success(
              $localize`:@@operatorCatalog.action.uninstalled:Uninstalled`,
              $localize`:@@operatorCatalog.action.uninstalledDetail:${entry.name} uninstalled`,
            );
            this.load();
          }),
          catchError((err) => {
            this.notificationService.error(
              $localize`:@@operatorCatalog.action.uninstallFailed:Uninstall failed`,
              this.extractError(err, $localize`:@@operatorCatalog.action.uninstallFailed:Uninstall failed`),
            );
            return of(null);
          }),
        )
        .subscribe(),
    );
  }

  delete(entry: OperatorCatalogEntry): void {
    this.subscriptions.add(
      this.operatorCatalogService
        .delete(entry.id)
        .pipe(
          tap(() => {
            this.notificationService.success(
              $localize`:@@operatorCatalog.action.deleted:Deleted`,
              $localize`:@@operatorCatalog.action.deletedDetail:${entry.name} removed from catalog`,
            );
            this.load();
          }),
          catchError((err) => {
            this.notificationService.error(
              $localize`:@@operatorCatalog.action.deleteFailed:Delete failed`,
              this.extractError(err, $localize`:@@operatorCatalog.action.deleteFailed:Delete failed`),
            );
            return of(null);
          }),
        )
        .subscribe(),
    );
  }

  formatDate(value?: string): string {
    if (!value) {
      return '-';
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  private toPayload(form: CatalogFormModel): OperatorCatalogPayload {
    return {
      name: form.name.trim(),
      packageName: form.packageName.trim(),
      channel: form.channel.trim() || undefined,
      targetNamespace: form.targetNamespace.trim(),
      desiredVersion: form.desiredVersion.trim(),
      uninstallPolicy: form.uninstallPolicy,
      manifestYaml: form.manifestYaml,
      managedCrds: this.parseManagedCrds(form.managedCrdsRaw),
    };
  }

  private parseManagedCrds(raw: string): ManagedCrdRef[] {
    return raw
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => !!line)
      .map((line) => {
        const [group = '', version = '', plural = '', namespaced = 'true'] = line.split(',').map((item) => item.trim());
        return {
          group,
          version,
          plural,
          namespaced: namespaced.toLowerCase() !== 'false',
        };
      });
  }

  private stringifyManagedCrds(managedCrds: ManagedCrdRef[] | undefined): string {
    if (!managedCrds?.length) {
      return '';
    }
    return managedCrds
      .map((item) => `${item.group},${item.version},${item.plural},${item.namespaced !== false}`)
      .join('\n');
  }

  private extractError(err: any, fallback: string): string {
    if (typeof err?.error === 'string' && err.error.trim().length) {
      return err.error;
    }
    if (typeof err?.message === 'string' && err.message.trim().length) {
      return err.message;
    }
    return fallback;
  }

  private emptyForm(): CatalogFormModel {
    return {
      name: '',
      packageName: '',
      channel: 'stable',
      targetNamespace: 'operators',
      desiredVersion: '1.0.0',
      uninstallPolicy: 'RETAIN_CRDS',
      manifestYaml: '',
      managedCrdsRaw: '',
    };
  }

  loadHubOperators(reset = false): void {
    if (reset) {
      this.hubPage = 1;
    }
    this.hubLoading = true;
    this.subscriptions.add(
      this.operatorHubService
        .list(this.hubQuery, this.hubPage, this.hubSize)
        .pipe(
          tap((response) => {
            const items = response?.items || [];
            this.hubTotal = response?.total || 0;
            this.hubOperators = items;
          }),
          catchError((err) => {
            this.notificationService.error(
              $localize`:@@operatorHub.error.list:Failed to load OperatorHub operators`,
              this.extractError(err, $localize`:@@operatorHub.error.list:Failed to load OperatorHub operators`),
            );
            return of(null);
          }),
          finalize(() => (this.hubLoading = false)),
        )
        .subscribe(),
    );
  }

  onHubPageChange(event: PaginatorState): void {
    const pageZeroBased = event.page ?? 0;
    const rows = event.rows ?? this.hubSize;
    this.hubSize = rows;
    this.hubPage = pageZeroBased + 1;
    this.loadHubOperators(false);
  }

  onHubSearch(): void {
    this.loadHubOperators(true);
  }
}
