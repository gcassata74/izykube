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

@Component({
  selector: 'app-service-form',
  templateUrl: './service-form.component.html',
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
  private portManuallyEdited = false;

  serviceTypes = [
    { name: 'ClusterIP', value: 'ClusterIP' },
    { name: 'NodePort', value: 'NodePort' },
    { name: 'LoadBalancer', value: 'LoadBalancer' }
  ];

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService
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
  }

  private initForm() {
    this.portManuallyEdited = false;
    const service = this.selectedNode as Service;
    this.form = this.fb.group({
      name: [service.name || '', Validators.required],
      type: [service.type || 'ClusterIP', Validators.required],
      port: [service.port, [Validators.required, Validators.min(1), Validators.max(65535)]],
      nodePort: [service.nodePort, [Validators.min(30000), Validators.max(32767)]],
      exposeService: [service.exposeService ?? false],
      frontendUrl: [{ value: service.frontendUrl || '', disabled: !(service.exposeService ?? false) }]
    });

    this.form.get('type')?.valueChanges.subscribe((value) => {
      if (value === 'NodePort') {
        this.form.get('nodePort')?.setValue(this.form.get('nodePort')?.value ?? 30000, { emitEvent: false });
      } else {
        this.form.get('nodePort')?.setValue(null, { emitEvent: false });
      }
    });

    this.form.get('exposeService')?.valueChanges.subscribe((value) => {
      const frontendUrlControl = this.form.get('frontendUrl');
      if (value) {
        frontendUrlControl?.enable({ emitEvent: false });
      } else {
        frontendUrlControl?.disable({ emitEvent: false });
        frontendUrlControl?.setValue('', { emitEvent: false });
      }
    });

    this.form.get('port')?.valueChanges.subscribe(() => {
      if (this.form.get('port')?.dirty) {
        this.portManuallyEdited = true;
      }
    });
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
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
    if (nameControl && (!nameControl.value || nameControl.pristine)) {
      nameControl.patchValue(`${defaultDeployment.name}-service`, { emitEvent: false });
    }

    const portControl = this.form.get('port');
    if (portControl && (!this.portManuallyEdited || portControl.pristine)) {
      const derivedPort = this.derivePortFromDeployment(defaultDeployment);
      if (derivedPort) {
        portControl.patchValue(derivedPort, { emitEvent: false });
      }
    }
  }

  private derivePortFromDeployment(deployment: Deployment): number | null {
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
}
