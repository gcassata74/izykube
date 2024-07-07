// service-form.component.ts
import { Component, Input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AutoSaveService } from '../../../services/auto-save.service';
import { Service } from '../../../model/service.class';
import { Node } from '../../../model/node.class';

@Component({
  selector: 'app-service-form',
  templateUrl: './service-form.component.html',
  styleUrls: ['./service-form.component.scss'],
  providers: [AutoSaveService]
})
export class ServiceFormComponent implements OnInit {
  @Input() selectedNode!: Node;
  form!: FormGroup;

  serviceTypes = ['ClusterIP', 'NodePort', 'LoadBalancer', 'ExternalName'];

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
      name: [service.name, Validators.required],
      type: [service.type, Validators.required],
      port: [service.port, [Validators.required, Validators.min(1), Validators.max(65535)]],
      targetPort: [service.targetPort, [Validators.required, Validators.min(1), Validators.max(65535)]],
      protocol: [service.protocol || 'TCP', Validators.required],
      nodePort: [service.nodePort, [Validators.min(30000), Validators.max(32767)]]
    });

    this.form.get('type')?.valueChanges.subscribe(type => {
      if (type === 'NodePort') {
        this.form.get('nodePort')?.setValidators([Validators.required, Validators.min(30000), Validators.max(32767)]);
      } else {
        this.form.get('nodePort')?.clearValidators();
      }
      this.form.get('nodePort')?.updateValueAndValidity();
    });
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }
}