import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { finalize, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import { IngressGatewayInfo, IngressSummary, NamespaceOption, ServiceSummary } from '../model/kube-summary';
import { MenuItem } from 'primeng/api';
import { Menu } from 'primeng/menu';
import { KubeExplorerService } from '../services/kube-explorer.service';
import { NotificationService } from '../services/notification.service';
import { Table } from 'primeng/table';
import { RoutesService } from '../services/routes.service';

@Component({
  selector: 'app-routes',
  templateUrl: './routes.component.html',
  styleUrls: ['./routes.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RoutesComponent implements OnInit {
  namespaces: NamespaceOption[] = [];
  selectedNamespace = 'all';
  routes: IngressSummary[] = [];
  services: ServiceSummary[] = [];
  loading = false;
  filterValue = '';
  gatewayInfo: IngressGatewayInfo | null = null;
  createDialogVisible = false;
  createForm!: FormGroup;
  createSubmitting = false;
  servicePortOptions: number[] = [];
  editingRoute: IngressSummary | null = null;

  @ViewChild('routesTable') routesTable?: Table;

  constructor(
    private kubeExplorerService: KubeExplorerService,
    private notificationService: NotificationService,
    private routesService: RoutesService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadNamespaces();
    this.refreshRoutes();
    this.loadIngressGateway();
    this.buildForm();
  }

  loadNamespaces(): void {
    this.kubeExplorerService.getNamespaces().subscribe({
      next: (namespaces) => {
        this.namespaces = [{ name: 'all' }, ...namespaces.map((name) => ({ name }))];
        this.cdr.markForCheck();
      },
      error: () => {
        this.notificationService.error('Failed to load namespaces');
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
          this.routes = summary?.ingresses || [];
          this.services = summary?.services || [];
          this.applyFilter(this.filterValue);
        },
        error: (error) => {
          const detail = error?.error || error?.message || 'Unable to load routes.';
          this.notificationService.error('Routes load failed', typeof detail === 'string' ? detail : undefined);
        }
      });
  }

  loadIngressGateway(): void {
    this.kubeExplorerService.getIngressGatewayInfo().subscribe({
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
      this.notificationService.warn('Select a namespace', 'Choose a namespace before creating a route.');
      return;
    }
    this.editingRoute = null;
    this.createForm.reset({
      name: '',
      host: '',
      path: '/',
      serviceName: '',
      servicePort: null,
      tlsSecret: ''
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

  editRoute(route: IngressSummary): void {
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
      path: '/',
      serviceName,
      servicePort,
      tlsSecret: route.tls || ''
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
      this.notificationService.warn('Service required', 'Select a service before saving the route.');
      this.createForm.get('serviceName')?.markAsTouched();
      return;
    }
    if (!formValue.servicePort) {
      this.notificationService.warn('Service port required', 'Select a service port before saving the route.');
      this.createForm.get('servicePort')?.markAsTouched();
      return;
    }
    this.createSubmitting = true;
    const payload = {
      namespace: this.selectedNamespace,
      name: formValue.name?.trim(),
      host: formValue.host?.trim(),
      path: formValue.path?.trim() || '/',
      ingressClassName: 'izykube-class',
      serviceName: formValue.serviceName,
      servicePort: Number(formValue.servicePort),
      tlsSecret: formValue.tlsSecret?.trim() || undefined
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
        const message = this.editingRoute ? 'Route updated successfully.' : 'Route created successfully.';
        this.notificationService.success('Route saved', message);
        this.createDialogVisible = false;
        this.editingRoute = null;
        this.refreshRoutes();
      },
      error: (error: any) => {
        const detail = error?.error || error?.message || 'Unable to save route.';
        this.notificationService.error('Save failed', typeof detail === 'string' ? detail : undefined);
      }
    });
  }

  deleteRoute(route: IngressSummary): void {
    if (!route?.name || !route?.namespace) {
      return;
    }
    const confirmed = window.confirm(`Delete route "${route.name}" in namespace "${route.namespace}"?`);
    if (!confirmed) {
      return;
    }
    this.createSubmitting = true;
    this.routesService.deleteRoute(route.namespace, route.name)
      .pipe(finalize(() => {
        this.createSubmitting = false;
        this.cdr.markForCheck();
      }))
      .subscribe({
        next: () => {
          this.notificationService.success('Route deleted', `Deleted route ${route.name}.`);
          this.refreshRoutes();
        },
        error: (error: any) => {
          const detail = error?.error || error?.message || 'Unable to delete route.';
          this.notificationService.error('Delete failed', typeof detail === 'string' ? detail : undefined);
        }
      });
  }

  openRoute(route: IngressSummary, useTls: boolean): void {
    const url = this.buildRouteUrl(route, useTls);
    if (!url) {
      this.notificationService.warn('Route unavailable', 'Gateway endpoint not available yet.');
      return;
    }
    window.open(url, '_blank', 'noopener');
  }

  toggleRouteMenu(event: Event, menu: Menu): void {
    event.stopPropagation();
    menu.toggle(event);
  }

  getRouteMenuItems(route: IngressSummary): MenuItem[] {
    return [
      {
        label: 'Open HTTP',
        icon: 'pi pi-globe',
        disabled: !this.canOpenRoute(false),
        command: () => this.openRoute(route, false),
      },
      {
        label: 'Open HTTPS',
        icon: 'pi pi-lock',
        disabled: !this.canOpenRoute(true),
        command: () => this.openRoute(route, true),
      }
    ];
  }

  private buildForm(): void {
    this.createForm = this.fb.group({
      name: ['', Validators.required],
      host: [''],
      path: ['/', [Validators.required, Validators.pattern(/^\//)]],
      serviceName: ['', Validators.required],
      servicePort: [null, [Validators.required, Validators.min(1), Validators.max(65535)]],
      tlsSecret: ['']
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

  private canOpenRoute(useTls: boolean): boolean {
    if (!this.gatewayInfo || !this.gatewayInfo.host) {
      return false;
    }
    const port = useTls ? this.gatewayInfo.httpsPort : this.gatewayInfo.httpPort;
    return !!port;
  }

  private buildRouteUrl(route: IngressSummary, useTls: boolean): string | null {
    if (!this.gatewayInfo || !this.gatewayInfo.host) {
      return null;
    }
    const port = useTls ? this.gatewayInfo.httpsPort : this.gatewayInfo.httpPort;
    if (!port) {
      return null;
    }
    const host = this.selectRouteHost(route);
    const scheme = useTls ? 'https' : 'http';
    const path = route.path?.startsWith('/') ? route.path : `/${route.path || ''}`;
    const needsPort = !(this.gatewayInfo.loadBalancer && ((useTls && port === 443) || (!useTls && port === 80)));
    const portSuffix = needsPort ? `:${port}` : '';
    return `${scheme}://${host}${portSuffix}${path}`;
  }

  private selectRouteHost(route: IngressSummary): string {
    if (!route.hosts || route.hosts.includes('<all hosts>')) {
      return this.gatewayInfo?.host || '';
    }
    return route.hosts.split(',')[0]?.trim() || this.gatewayInfo?.host || '';
  }
}
