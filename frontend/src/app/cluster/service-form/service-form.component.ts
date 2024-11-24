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
import { Component, Input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Node } from '../../model/node.class';
import { AutoSaveService } from '../../services/auto-save.service';
import { Service } from '../../model/service.class';

@Component({
  selector: 'app-service-form',
  templateUrl: './service-form.component.html',
  providers: [AutoSaveService]
})
export class ServiceFormComponent implements OnInit {
  @Input() selectedNode!: Node;
  @Input() connectedDeployment?: Node;

  form!: FormGroup;

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
  }

  private initForm() {
    const service = this.selectedNode as Service;
    this.form = this.fb.group({
      name: [service.name || (this.connectedDeployment ? this.connectedDeployment.name + '-service' : ''), Validators.required],
      type: [service.type || 'ClusterIP', Validators.required],
      port: [service.port, [Validators.required, Validators.min(1), Validators.max(65535)]],
      nodePort: [service.nodePort, [Validators.min(30000), Validators.max(32767)]],
      exposeService: [service.exposeService || false],
      frontendUrl: [service.frontendUrl || '']
    });

    this.form.get('type')?.valueChanges.subscribe((value) => {
      if (value === 'NodePort') {
        this.form.get('nodePort')?.setValue(30000);
      } else {
        this.form.get('nodePort')?.setValue(null);
      }
    });

    this.form.get('exposeService')?.valueChanges.subscribe((value) => {
      if (value) {
        this.form.get('frontendUrl')?.enable();
      } else {
        this.form.get('frontendUrl')?.disable();
        this.form.get('frontendUrl')?.setValue('');
      }
    });
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }
}