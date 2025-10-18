import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AutoSaveService } from '../../services/auto-save.service';
import { Node } from '../../model/node.class';
import { Ingress } from '../../model/ingress.class';
import { Service } from '../../model/service.class';

@Component({
  selector: 'app-ingress-form',
  templateUrl: './ingress-form.component.html',
  providers: [AutoSaveService]
})
export class IngressFormComponent implements OnInit, OnChanges {
  @Input() selectedNode!: Node;
  @Input() sourceNodes: Node[] = [];

  ingressForm!: FormGroup;
  services: Service[] = [];
  serviceOptions: { label: string; value: string }[] = [];
  private servicePortManuallyEdited = false;

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.setupAutoSave();
    this.applyLinkedServiceDefaults();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['sourceNodes'] || changes['cluster']) && this.ingressForm) {
      this.applyLinkedServiceDefaults();
    }
  }

  private initForm(): void {
    const ingress = this.selectedNode as Ingress;
    this.ingressForm = this.fb.group({
      name: [ingress.name, Validators.required],
      host: [ingress.host, Validators.required],
      path: [ingress.path, Validators.required],
      serviceName: [ingress.serviceName || '', Validators.required],
      servicePort: [ingress.servicePort || 80, [Validators.required, Validators.min(1), Validators.max(65535)]]
    });

    this.ingressForm.get('servicePort')?.valueChanges.subscribe(() => {
      if (this.ingressForm.get('servicePort')?.dirty) {
        this.servicePortManuallyEdited = true;
      }
    });

    this.ingressForm.get('serviceName')?.valueChanges.subscribe((serviceName) => {
      this.updateServicePortFor(serviceName);
    });
  }

  private setupAutoSave(): void {
    this.autoSaveService.enableAutoSave(this.ingressForm, this.selectedNode.id, this.ingressForm.valueChanges);
  }

  private applyLinkedServiceDefaults(): void {
    if (!this.ingressForm) {
      return;
    }

    this.services = (this.sourceNodes || [])
      .filter(node => node.kind?.toLowerCase() === 'service')
      .map(node => node as Service);

    this.serviceOptions = this.services.map(service => ({
      label: service.name,
      value: service.name
    }));

    if (!this.services.length) {
      return;
    }

    const serviceNameControl = this.ingressForm.get('serviceName');
    const currentName = serviceNameControl?.value;
    const hasMatchingService = currentName && this.services.some(service => service.name === currentName);

    if (!hasMatchingService) {
      serviceNameControl?.patchValue(this.services[0].name, { emitEvent: false });
      this.servicePortManuallyEdited = false;
    }

    this.updateServicePortFor(serviceNameControl?.value, !hasMatchingService);
  }

  private updateServicePortFor(serviceName: string, forceUpdate = false): void {
    if (!serviceName) {
      return;
    }

    const matchingService = this.services.find(service => service.name === serviceName);
    const portControl = this.ingressForm.get('servicePort');

    if (!matchingService || !portControl) {
      return;
    }

    if (!this.servicePortManuallyEdited || forceUpdate || portControl.pristine) {
      portControl.patchValue(matchingService.port, { emitEvent: false });
    }

    const hostControl = this.ingressForm.get('host');
    if (hostControl && (!hostControl.value || hostControl.pristine) && matchingService.frontendUrl) {
      hostControl.patchValue(this.stripHttpPrefix(matchingService.frontendUrl), { emitEvent: false });
    }
  }

  private stripHttpPrefix(url: string): string {
    return url.replace(/^(http:\/\/|https:\/\/)/, '');
  }
}
