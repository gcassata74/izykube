import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { finalize, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import { IstioGatewayInfo, RouteSummary, NamespaceOption, ServiceSummary } from '../model/kube-summary';
import { KubeExplorerService } from '../services/kube-explorer.service';
import { NotificationService } from '../services/notification.service';
import { Table } from 'primeng/table';
import { RoutesService } from '../services/routes.service';
import { ConfirmationService } from 'primeng/api';

@Component({
  selector: 'app-routes',
  templateUrl: './routes.component.html',
  styleUrls: ['./routes.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RoutesComponent implements OnInit {
  private readonly webPorts = new Set<number>([80, 81, 443, 8080, 8081, 8443, 3000, 4200, 5000, 5173, 8000, 8888, 9000]);
  namespaces: NamespaceOption[] = [];
  selectedNamespace = 'all';
  routes: RouteSummary[] = [];
  services: ServiceSummary[] = [];
  loading = false;
  filterValue = '';
  gatewayInfo: IstioGatewayInfo | null = null;
  createDialogVisible = false;
  createForm!: FormGroup;
  createSubmitting = false;
  servicePortOptions: number[] = [];
  editingRoute: RouteSummary | null = null;
  private readonly httpsOverrideTtlMs = 60_000;
  private readonly httpsOverrides = new Map<string, { enabled: boolean; expiresAt: number }>();

  @ViewChild('routesTable') routesTable?: Table;

  constructor(
    private kubeExplorerService: KubeExplorerService,
    private notificationService: NotificationService,
    private routesService: RoutesService,
    private confirmationService: ConfirmationService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadNamespaces();
    this.refreshRoutes();
    this.loadIstioGateway();
    this.buildForm();
  }

  loadNamespaces(): void {
    this.kubeExplorerService.getNamespaces().subscribe({
      next: (namespaces) => {
        this.namespaces = [{ name: 'all' }, ...namespaces.map((name) => ({ name }))];
        this.cdr.markForCheck();
      },
      error: () => {
        this.notificationService.error($localize`:@@routes.error.loadNamespaces:Failed to load namespaces`);
      }
    });
  }

  onNamespaceChange(namespace: string): void {
    this.selectedNamespace = namespace || 'all';
    this.refreshRoutes();
  }

  refreshRoutes(): void {
    this.loading = true;
    this.kubeExplorerService.getNamespaceSummary(this.selectedNamespace)
      .pipe(finalize(() => {
        this.loading = false;
        this.cdr.markForCheck();
      }))
      .subscribe({
        next: (summary) => {
          this.routes = summary?.routes || [];
          this.services = summary?.services || [];
          this.reconcileHttpsOverrides();
          this.applyFilter(this.filterValue);
        },
        error: (error) => {
          const detail = error?.error || error?.message || $localize`:@@routes.error.loadRoutesDetail:Unable to load routes.`;
          this.notificationService.error($localize`:@@routes.error.loadRoutes:Routes load failed`, typeof detail === 'string' ? detail : undefined);
        }
      });
  }

  loadIstioGateway(): void {
    this.kubeExplorerService.getIstioGatewayInfo().subscribe({
      next: (gatewayInfo) => {
        this.gatewayInfo = gatewayInfo;
        this.cdr.markForCheck();
      },
      error: () => {
        this.gatewayInfo = null;
        this.cdr.markForCheck();
      }
    });
  }

  onFilterChange(value: string): void {
    this.filterValue = value;
    this.applyFilter(value);
  }

  clearFilter(): void {
    this.filterValue = '';
    this.applyFilter('');
  }

  openCreateDialog(): void {
    if (this.selectedNamespace === 'all') {
      this.notificationService.warn(
        $localize`:@@routes.warn.selectNamespaceTitle:Select a namespace`,
        $localize`:@@routes.warn.selectNamespaceDetail:Choose a namespace before creating a route.`
      );
      return;
    }
    this.editingRoute = null;
    this.createForm.reset({
      name: '',
      host: '',
      path: '/',
      serviceName: '',
      servicePort: null,
      httpsEnabled: false
    });
    this.servicePortOptions = [];
    this.createDialogVisible = true;
  }

  closeCreateDialog(): void {
    if (this.createSubmitting) {
      return;
    }
    this.createDialogVisible = false;
  }

  editRoute(route: RouteSummary): void {
    if (!route?.namespace || !route?.name) {
      return;
    }
    this.editingRoute = route;
    if (this.selectedNamespace !== route.namespace) {
      this.selectedNamespace = route.namespace;
      this.refreshRoutes();
    }
    const serviceName = this.extractPrimaryServiceName(route.serviceTargets);
    const servicePort = this.extractPrimaryServicePort(route.serviceTargets);
    this.createForm.reset({
      name: route.name,
      host: route.hosts?.includes('<all hosts>') ? '' : route.hosts,
      path: route.path || '/',
      serviceName,
      servicePort,
      httpsEnabled: this.isTlsConfigured(route)
    });
    this.onServiceChange(serviceName);
    this.createDialogVisible = true;
  }

  onServiceChange(serviceName: string): void {
    const service = this.services.find((svc) => svc.name === serviceName);
    const ports = this.parseServicePorts(service?.ports);
    this.servicePortOptions = ports;
    if (ports.length === 1) {
      this.createForm.get('servicePort')?.setValue(ports[0]);
    } else if (!ports.includes(this.createForm.get('servicePort')?.value)) {
      this.createForm.get('servicePort')?.setValue(null);
    }
    this.createForm.get('servicePort')?.markAsTouched();
  }

  submitCreate(): void {
    if (this.createSubmitting || !this.createForm.valid) {
      this.createForm.markAllAsTouched();
      return;
    }
    const formValue = this.createForm.value;
    if (!formValue.serviceName) {
      this.notificationService.warn(
        $localize`:@@routes.warn.serviceRequiredTitle:Service required`,
        $localize`:@@routes.warn.serviceRequiredDetail:Select a service before saving the route.`
      );
      this.createForm.get('serviceName')?.markAsTouched();
      return;
    }
    if (!formValue.servicePort) {
      this.notificationService.warn(
        $localize`:@@routes.warn.servicePortRequiredTitle:Service port required`,
        $localize`:@@routes.warn.servicePortRequiredDetail:Select a service port before saving the route.`
      );
      this.createForm.get('servicePort')?.markAsTouched();
      return;
    }
    if (!this.isWebPort(Number(formValue.servicePort))) {
      this.notificationService.warn(
        $localize`:@@routes.warn.nonHttpPortTitle:Unsupported route type`,
        $localize`:@@routes.warn.nonHttpPortDetail:HTTP/HTTPS routes are allowed only for web service ports. Configure TCP routes manually for database/message-broker ports.`
      );
      this.createForm.get('servicePort')?.markAsTouched();
      return;
    }
    this.createSubmitting = true;
    const payload = {
      namespace: this.selectedNamespace,
      name: formValue.name?.trim(),
      host: formValue.host?.trim(),
      path: formValue.path?.trim() || '/',
      serviceName: formValue.serviceName,
      servicePort: Number(formValue.servicePort),
      httpsEnabled: !!formValue.httpsEnabled
    };
    let request$;
    if (this.editingRoute) {
      const originalName = this.editingRoute.name;
      const newName = payload.name;
      if (newName && newName !== originalName) {
        request$ = this.routesService.createRoute(payload).pipe(
          switchMap(() => this.routesService.deleteRoute(this.selectedNamespace, originalName)),
          switchMap(() => this.routesService.updateRoute(this.selectedNamespace, newName, payload)),
        );
      } else {
        request$ = this.routesService.updateRoute(this.selectedNamespace, originalName, payload);
      }
    } else {
      request$ = this.routesService.createRoute(payload);
    }

    request$.pipe(
      finalize(() => {
        this.createSubmitting = false;
        this.cdr.markForCheck();
      })
    ).subscribe({
      next: () => {
        const message = this.editingRoute
          ? $localize`:@@routes.success.updatedDetail:Route updated successfully.`
          : $localize`:@@routes.success.createdDetail:Route created successfully.`;
        this.notificationService.success($localize`:@@routes.success.savedTitle:Route saved`, message);
        this.createDialogVisible = false;
        const key = this.routeKey(payload.namespace, payload.name);
        this.httpsOverrides.set(key, {
          enabled: !!payload.httpsEnabled,
          expiresAt: Date.now() + this.httpsOverrideTtlMs
        });
        this.editingRoute = null;
        this.refreshRoutes();
      },
      error: (error: any) => {
        const detail = error?.error || error?.message || $localize`:@@routes.error.saveDetail:Unable to save route.`;
        this.notificationService.error($localize`:@@routes.error.save:Save failed`, typeof detail === 'string' ? detail : undefined);
      }
    });
  }

  deleteRoute(route: RouteSummary): void {
    if (!route?.name || !route?.namespace) {
      return;
    }
    this.confirmationService.confirm({
      header: $localize`:@@routes.confirm.deleteTitle:Delete route`,
      message: $localize`:@@routes.confirm.deleteMessage:Delete route "${route.name}:routeName:" in namespace "${route.namespace}:namespace:"?`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: $localize`:@@common.delete:Delete`,
      rejectLabel: $localize`:@@common.cancel:Cancel`,
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.createSubmitting = true;
        this.routesService.deleteRoute(route.namespace, route.name)
          .pipe(finalize(() => {
            this.createSubmitting = false;
            this.cdr.markForCheck();
          }))
          .subscribe({
            next: () => {
              this.notificationService.success(
                $localize`:@@routes.success.deletedTitle:Route deleted`,
                $localize`:@@routes.success.deletedDetail:Deleted route ${route.name}:routeName:.`
              );
              this.refreshRoutes();
            },
            error: (error: any) => {
              const detail = error?.error || error?.message || $localize`:@@routes.error.deleteDetail:Unable to delete route.`;
              this.notificationService.error($localize`:@@routes.error.delete:Delete failed`, typeof detail === 'string' ? detail : undefined);
            }
          });
      }
    });
  }

  openRoute(route: RouteSummary, useTls: boolean): void {
    const url = this.buildRouteUrl(route, useTls);
    if (!url) {
      this.notificationService.warn(
        $localize`:@@routes.warn.unavailableTitle:Route unavailable`,
        $localize`:@@routes.warn.unavailableDetail:Gateway endpoint not available yet.`
      );
      return;
    }
    window.open(url, '_blank', 'noopener');
  }

  prefersTls(route: RouteSummary): boolean {
    const override = this.getHttpsOverride(route.namespace, route.name);
    if (override !== null) {
      return override;
    }
    return !!route?.tls;
  }

  private routeKey(namespace: string | null | undefined, name: string | null | undefined): string {
    return `${namespace || ''}/${name || ''}`;
  }

  private getHttpsOverride(namespace: string, name: string): boolean | null {
    const key = this.routeKey(namespace, name);
    const override = this.httpsOverrides.get(key);
    if (!override) {
      return null;
    }
    if (override.expiresAt < Date.now()) {
      this.httpsOverrides.delete(key);
      return null;
    }
    return override.enabled;
  }

  private reconcileHttpsOverrides(): void {
    const now = Date.now();
    for (const [key, value] of this.httpsOverrides.entries()) {
      if (value.expiresAt < now) {
        this.httpsOverrides.delete(key);
      }
    }
    for (const route of this.routes) {
      const key = this.routeKey(route.namespace, route.name);
      if (route.tls) {
        this.httpsOverrides.delete(key);
      }
    }
  }

  getRouteUrl(route: RouteSummary): string | null {
    const routePort = this.extractPrimaryServicePort(route.serviceTargets);
    if (!this.isWebPort(routePort)) {
      return null;
    }
    const useTls = this.prefersTls(route);
    return this.buildRouteUrl(route, useTls);
  }

  isTlsConfigured(route: RouteSummary): boolean {
    return this.prefersTls(route);
  }

  onTestRouteClick(event: Event, route: RouteSummary): void {
    const url = this.getRouteUrl(route);
    if (!url) {
      event.preventDefault();
      this.notificationService.warn(
        $localize`:@@routes.warn.unavailableTitle:Route unavailable`,
        $localize`:@@routes.warn.unavailableDetail:Gateway endpoint not available yet.`
      );
    }
  }

  get dialogHeader(): string {
    return this.editingRoute
      ? $localize`:@@routes.dialog.editTitle:Edit Route`
      : $localize`:@@routes.dialog.createTitle:Create Route`;
  }
  get submitButtonLabel(): string {
    return this.editingRoute
      ? $localize`:@@common.save:Save`
      : $localize`:@@common.create:Create`;
  }

  private buildForm(): void {
    this.createForm = this.fb.group({
      name: ['', Validators.required],
      host: [''],
      path: ['/', [Validators.required, Validators.pattern(/^\//)]],
      serviceName: ['', Validators.required],
      servicePort: [null, [Validators.required, Validators.min(1), Validators.max(65535)]],
      httpsEnabled: [false]
    });
  }

  private applyFilter(value: string): void {
    if (!this.routesTable) {
      return;
    }
    this.routesTable.filterGlobal(value, 'contains');
  }

  private parseServicePorts(ports?: string): number[] {
    if (!ports) {
      return [];
    }
    return ports
      .split(',')
      .map((entry) => entry.trim())
      .map((entry) => entry.split('/')[0])
      .map((entry) => Number(entry))
      .filter((entry) => !Number.isNaN(entry));
  }

  private extractPrimaryServiceName(services?: string): string {
    if (!services) {
      return '';
    }
    const primary = services.split(',')[0]?.trim() || '';
    return primary.split(':')[0]?.trim() || '';
  }

  private extractPrimaryServicePort(services?: string): number | null {
    if (!services) {
      return null;
    }
    const primary = services.split(',')[0]?.trim() || '';
    const portPart = primary.split(':')[1]?.trim();
    if (!portPart) {
      return null;
    }
    const port = Number(portPart);
    return Number.isNaN(port) ? null : port;
  }

  canOpenRoute(route: RouteSummary, useTls: boolean): boolean {
    const routePort = this.extractPrimaryServicePort(route.serviceTargets);
    if (!this.isWebPort(routePort)) {
      return false;
    }
    const host = this.selectRouteHost(route);
    if (!host) {
      return false;
    }
    const port = useTls ? this.gatewayInfo?.httpsPort : this.gatewayInfo?.httpPort;
    return !!port;
  }

  private buildRouteUrl(route: RouteSummary, useTls: boolean): string | null {
    const port = useTls ? this.gatewayInfo?.httpsPort : this.gatewayInfo?.httpPort;
    if (!port) {
      return null;
    }
    const host = this.selectRouteHost(route);
    if (!host) {
      return null;
    }
    const scheme = useTls ? 'https' : 'http';
    const path = route.path?.startsWith('/') ? route.path : `/${route.path || ''}`;
    const needsPort = !(this.gatewayInfo?.loadBalancer && ((useTls && port === 443) || (!useTls && port === 80)));
    const portSuffix = needsPort ? `:${port}` : '';
    return `${scheme}://${host}${portSuffix}${path}`;
  }

  private selectRouteHost(route: RouteSummary): string {
    if (!route.hosts) {
      return this.gatewayInfo?.host || '';
    }
    const trimmedHosts = route.hosts.trim();
    if (!trimmedHosts) {
      return this.gatewayInfo?.host || '';
    }
    if (trimmedHosts.includes('<all hosts>')) {
      return this.gatewayInfo?.host || '';
    }
    return trimmedHosts.split(',')[0]?.trim() || this.gatewayInfo?.host || '';
  }

  isSelectedServicePortWeb(): boolean {
    const servicePort = Number(this.createForm?.get('servicePort')?.value);
    if (Number.isNaN(servicePort) || servicePort < 1) {
      return true;
    }
    return this.isWebPort(servicePort);
  }

  private isWebPort(port: number | null): boolean {
    return typeof port === 'number' && this.webPorts.has(port);
  }
}
