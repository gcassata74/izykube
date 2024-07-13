import { Component, Input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Node } from '../../../model/node.class';
import { AutoSaveService } from '../../../services/auto-save.service';

@Component({
  selector: 'app-service-form',
  templateUrl: './service-form.component.html',
  styleUrls: ['./service-form.component.scss'],
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
    const service = this.selectedNode as any; // Cast to any for now
    this.form = this.fb.group({
      name: [service.name || (this.connectedDeployment ? this.connectedDeployment.name + '-service' : ''), Validators.required],
      type: [service.type || 'ClusterIP', Validators.required],
      port: [service.port, [Validators.required, Validators.min(1), Validators.max(65535)]],
      nodePort: [service.nodePort, [Validators.min(30000), Validators.max(32767)]]
    });
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }
}