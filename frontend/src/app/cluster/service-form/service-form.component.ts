/**
 * IzyKube - Enterprise Kubernetes Management Platform
 * Copyright (C) 2024 IzyLife Corporation. All rights reserved.
 * 
 * This file is part of IzyKube, an enterprise Kubernetes management platform
 * developed by IzyLife Corporation. Unauthorized copying or redistribution of this file 
 * in source and binary forms via any medium is strictly prohibited.
 * 
 * IzyKube is proprietary software of IzyLife Corporation. 
 * No warranty, explicit or implicit, provided.
 * 
 * @author IzyLife Development Team
 * @version 1.0.0
 * @since March 2024
 */
import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Node } from '../../model/node.class';
import { AutoSaveService } from '../../services/auto-save.service';
import { Service } from '../../model/service.class';
import { Cluster } from '../../model/cluster.class';
import { Deployment } from '../../model/deployment.class';
import { Container } from '../../model/container.class';
import { NotificationService } from '../../services/notification.service';
import { PortForwardService } from '../../services/port-forward.service';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-service-form',
  templateUrl: './service-form.component.html',
  styleUrls: ['./service-form.component.scss'],
  providers: [AutoSaveService]
})
export class ServiceFormComponent implements OnInit, OnChanges {
  @Input() selectedNode!: Node;
  @Input() sourceNodes: Node[] = [];
  @Input() targetNodes: Node[] = [];
  @Input() cluster?: Cluster;

  form!: FormGroup;
  deployments: Deployment[] = [];
  connectedDeploymentNames: string[] = [];
  forwardBusy = false;

  serviceTypes = [
    { name: 'ClusterIP', value: 'ClusterIP' },
    { name: 'NodePort', value: 'NodePort' },
    { name: 'LoadBalancer', value: 'LoadBalancer' }
  ];

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService,
    private notificationService: NotificationService,
    private portForwardService: PortForwardService
  ) {}

  ngOnInit() {
    this.initForm();
    this.setupAutoSave();
    this.applyLinkedDeploymentDefaults();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['targetNodes'] || changes['cluster']) && this.form) {
      this.applyLinkedDeploymentDefaults();
    }
    if (changes['selectedNode'] && !changes['selectedNode'].firstChange) {
      this.refreshFormFromNode(changes['selectedNode'].currentValue as Service);
    }
  }

  private initForm() {
    const service = this.selectedNode as Service;
    const suggestedTargetPort = this.derivePortFromDeployment(this.getPrimaryDeployment() || undefined) ?? service.port ?? 80;
    const initialForwardTargetPort = service.forwardTargetPort ?? suggestedTargetPort;
    const initialForwardPort = service.forwardPort ?? initialForwardTargetPort;
    this.form = this.fb.group({
      name: [service.name || '', Validators.required],
      type: [service.type || 'ClusterIP', Validators.required],
      port: [service.port, [Validators.required, Validators.min(1), Validators.max(65535)]],
      nodePort: [service.nodePort, [Validators.min(30000), Validators.max(32767)]],
      forwardEnabled: [service.forwardEnabled ?? false],
      forwardPort: [initialForwardPort, [Validators.min(1), Validators.max(65535)]],
      forwardTargetPort: [initialForwardTargetPort, [Validators.min(1), Validators.max(65535)]],
      forwardActive: [service.forwardActive ?? false]
    });

    this.form.get('type')?.valueChanges.subscribe((value) => {
      if (value === 'NodePort') {
        this.form.get('nodePort')?.setValue(this.form.get('nodePort')?.value ?? 30000, { emitEvent: false });
      } else {
        this.form.get('nodePort')?.setValue(null, { emitEvent: false });
      }
    });

    this.form.get('forwardEnabled')?.valueChanges.subscribe((value) => {
      const enabled = !!value;
      const forwardPort = this.form.get('forwardPort');
      const forwardTargetPort = this.form.get('forwardTargetPort');
      if (enabled) {
        forwardPort?.enable({ emitEvent: false });
        forwardTargetPort?.enable({ emitEvent: false });
        this.applyForwardDefaultsIfMissing();
      } else {
        forwardPort?.disable({ emitEvent: false });
        forwardTargetPort?.disable({ emitEvent: false });
        this.form.get('forwardActive')?.setValue(false, { emitEvent: false });
      }
      this.updateForwardValidators(enabled);
    });

    this.updateForwardValidators(this.form.get('forwardEnabled')?.value);
    if (!this.form.get('forwardEnabled')?.value) {
      this.form.get('forwardPort')?.disable({ emitEvent: false });
      this.form.get('forwardTargetPort')?.disable({ emitEvent: false });
    }

    // Additional control-specific listeners can be added here when needed
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }

  private refreshFormFromNode(service: Service): void {
    if (!this.form) {
      return;
    }

    this.form.patchValue({
      name: service.name || '',
      type: service.type || 'ClusterIP',
      port: service.port,
      nodePort: service.nodePort,
      forwardEnabled: service.forwardEnabled ?? false,
      forwardPort: service.forwardPort ?? null,
      forwardTargetPort: service.forwardTargetPort ?? null,
      forwardActive: service.forwardActive ?? false
    }, { emitEvent: false });

    const forwardEnabled = service.forwardEnabled ?? false;
    const forwardPort = this.form.get('forwardPort');
    const forwardTargetPort = this.form.get('forwardTargetPort');
    if (forwardEnabled) {
      forwardPort?.enable({ emitEvent: false });
      forwardTargetPort?.enable({ emitEvent: false });
    } else {
      forwardPort?.disable({ emitEvent: false });
      forwardTargetPort?.disable({ emitEvent: false });
    }
    this.updateForwardValidators(forwardEnabled);
  }

  private applyLinkedDeploymentDefaults(): void {
    if (!this.form) {
      return;
    }

    this.deployments = (this.targetNodes || [])
      .filter(node => node.kind?.toLowerCase() === 'deployment')
      .map(node => node as Deployment);

    this.connectedDeploymentNames = this.deployments.map(deployment => deployment.name);

    if (!this.deployments.length) {
      return;
    }

    const defaultDeployment = this.deployments[0];

    const nameControl = this.form.get('name');
    if (nameControl && this.shouldAutofillName(nameControl.value)) {
      nameControl.patchValue(this.buildDefaultServiceName(defaultDeployment.name), { emitEvent: false });
    }

    const portControl = this.form.get('port');
    const derivedPort = this.derivePortFromDeployment(defaultDeployment);
    if (portControl && derivedPort && this.shouldAutofillPort(portControl.value, derivedPort)) {
      portControl.patchValue(derivedPort, { emitEvent: false });
    }

    this.applyForwardDefaultsIfMissing(derivedPort ?? undefined);
  }

  private getPrimaryDeployment(): Deployment | null {
    const deployments = (this.targetNodes || [])
      .filter(node => node.kind?.toLowerCase() === 'deployment')
      .map(node => node as Deployment);
    return deployments[0] ?? null;
  }

  private applyForwardDefaultsIfMissing(derivedPort?: number): void {
    if (!this.form) {
      return;
    }
    const suggested = derivedPort ?? this.derivePortFromDeployment(this.getPrimaryDeployment() as Deployment) ?? this.form.get('port')?.value ?? 80;
    const targetControl = this.form.get('forwardTargetPort');
    const forwardControl = this.form.get('forwardPort');
    if (targetControl && !targetControl.value) {
      targetControl.patchValue(suggested, { emitEvent: false });
    }
    if (forwardControl && !forwardControl.value) {
      forwardControl.patchValue(suggested, { emitEvent: false });
    }
  }

  private updateForwardValidators(enabled: boolean): void {
    const forwardPort = this.form.get('forwardPort');
    const forwardTargetPort = this.form.get('forwardTargetPort');
    if (!forwardPort || !forwardTargetPort) {
      return;
    }
    if (enabled) {
      forwardPort.setValidators([Validators.required, Validators.min(1), Validators.max(65535)]);
      forwardTargetPort.setValidators([Validators.required, Validators.min(1), Validators.max(65535)]);
    } else {
      forwardPort.setValidators([Validators.min(1), Validators.max(65535)]);
      forwardTargetPort.setValidators([Validators.min(1), Validators.max(65535)]);
    }
    forwardPort.updateValueAndValidity({ emitEvent: false });
    forwardTargetPort.updateValueAndValidity({ emitEvent: false });
  }

  activateForward(): void {
    if (!this.form) {
      return;
    }
    if (!this.form.get('forwardEnabled')?.value) {
      this.notificationService.warn('Enable forwarding', 'Turn on the forward toggle before activating.');
      return;
    }
    const forwardPort = this.form.get('forwardPort')?.value;
    const targetPort = this.form.get('forwardTargetPort')?.value;
    if (!forwardPort || !targetPort) {
      this.notificationService.warn('Ports required', 'Provide both local and target ports.');
      return;
    }
    const serviceName = (this.selectedNode as Service)?.name;
    if (!serviceName) {
      this.notificationService.warn('Service name required', 'Set a service name before forwarding.');
      return;
    }
    const namespace = this.cluster?.nameSpace || 'default';
    this.forwardBusy = true;
    this.portForwardService.checkLocalPort(forwardPort).subscribe({
      next: (check) => {
        if (!check?.available) {
          this.forwardBusy = false;
          this.notificationService.error('Port unavailable', check?.message || `Port ${forwardPort} is not available.`);
          return;
        }
        this.portForwardService.startForward({
          namespace,
          serviceName,
          localPort: forwardPort,
          targetPort
        }).pipe(
          finalize(() => {
            this.forwardBusy = false;
          })
        ).subscribe({
          next: (response) => {
            if (response?.localPort && response.localPort !== forwardPort) {
              this.form.get('forwardPort')?.setValue(response.localPort);
            }
            this.form.get('forwardActive')?.setValue(true);
            const activePort = response?.localPort ?? forwardPort;
            this.notificationService.success('Port forward active', `Forwarding localhost:${activePort} → ${targetPort}`);
          },
          error: (error) => {
            const detail = error?.error || error?.message || 'Unable to start port forward.';
            this.notificationService.error('Port forward failed', typeof detail === 'string' ? detail : undefined);
          }
        });
      },
      error: (error) => {
        const detail = error?.error || error?.message || 'Unable to check port availability.';
        this.notificationService.error('Port check failed', typeof detail === 'string' ? detail : undefined);
        this.forwardBusy = false;
      }
    });
  }

  deactivateForward(): void {
    const forwardPort = this.form.get('forwardPort')?.value;
    const targetPort = this.form.get('forwardTargetPort')?.value;
    const serviceName = (this.selectedNode as Service)?.name;
    if (!serviceName) {
      this.form.get('forwardActive')?.setValue(false);
      return;
    }
    const namespace = this.cluster?.nameSpace || 'default';
    if (!forwardPort || !targetPort) {
      this.form.get('forwardActive')?.setValue(false);
      return;
    }
    this.forwardBusy = true;
    this.portForwardService.stopForward({
      namespace,
      serviceName,
      localPort: forwardPort,
      targetPort
    }).pipe(
      finalize(() => {
        this.forwardBusy = false;
      })
    ).subscribe({
      next: () => {
        this.form.get('forwardActive')?.setValue(false);
        this.notificationService.success('Port forward stopped', `Stopped forwarding localhost:${forwardPort}`);
      },
      error: (error) => {
        const detail = error?.error || error?.message || 'Unable to stop port forward.';
        this.notificationService.error('Stop failed', typeof detail === 'string' ? detail : undefined);
      },
    });
  }

  private derivePortFromDeployment(deployment?: Deployment | null): number | null {
    if (!deployment) {
      return null;
    }
    if (deployment?.containerPort) {
      return deployment.containerPort;
    }

    if (!this.cluster || !this.cluster.links?.length) {
      return null;
    }

    const containerIds = this.cluster.links
      .filter(link => link.source === deployment.id)
      .map(link => link.target);

    if (!containerIds.length) {
      return null;
    }

    const containers = (this.cluster.nodes || [])
      .filter(node => containerIds.includes(node.id) && node.kind?.toLowerCase() === 'container')
      .map(node => node as Container);

    const container = containers[0];
    return container?.containerPort ?? null;
  }

  private shouldAutofillName(currentValue: string | null | undefined): boolean {
    const normalized = (currentValue ?? '').trim().toLowerCase();
    if (!normalized) {
      return true;
    }
    const kindPlaceholder = (this.selectedNode?.kind ?? '').trim().toLowerCase();
    return normalized === kindPlaceholder;
  }

  private buildDefaultServiceName(baseDeploymentName: string): string {
    return `${baseDeploymentName}-service`;
  }

  private shouldAutofillPort(currentValue: number | null | undefined, derivedPort: number): boolean {
    if (currentValue === undefined || currentValue === null) {
      return true;
    }

    const defaultPort = this.getDefaultServicePort();
    if (currentValue === defaultPort && derivedPort !== defaultPort) {
      return true;
    }

    return false;
  }

  private getDefaultServicePort(): number {
    // keep in sync with NodeFactoryService defaults
    return 80;
  }
}
