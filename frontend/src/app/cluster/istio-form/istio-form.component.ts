import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Node } from '../../model/node.class';
import { Istio } from '../../model/istio.class';
import { AutoSaveService } from '../../services/auto-save.service';
import { Service } from '../../model/service.class';

@Component({
  selector: 'app-istio-form',
  templateUrl: './istio-form.component.html',
  providers: [AutoSaveService]
})
export class IstioFormComponent implements OnInit, OnChanges {
  @Input() selectedNode!: Node;
  @Input() sourceNodes: Node[] = [];

  form!: FormGroup;
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
    if ((changes['sourceNodes'] || changes['cluster']) && this.form) {
      this.applyLinkedServiceDefaults();
    }
  }

  private initForm(): void {
    const istio = this.selectedNode as Istio;
    this.form = this.fb.group({
      name: [istio.name, Validators.required],
      host: [istio.host, Validators.required],
      path: [istio.path, Validators.required],
      serviceName: [istio.serviceName || '', Validators.required],
      servicePort: [istio.servicePort || 80, [Validators.required, Validators.min(1), Validators.max(65535)]]
    });

    this.form.get('servicePort')?.valueChanges.subscribe(() => {
      if (this.form.get('servicePort')?.dirty) {
        this.servicePortManuallyEdited = true;
      }
    });

    this.form.get('serviceName')?.valueChanges.subscribe((serviceName) => {
      this.updateServicePortFor(serviceName);
    });
  }

  private setupAutoSave(): void {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }

  private applyLinkedServiceDefaults(): void {
    if (!this.form) {
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

    const serviceNameControl = this.form.get('serviceName');
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
    const portControl = this.form.get('servicePort');

    if (!matchingService || !portControl) {
      return;
    }

    if (!this.servicePortManuallyEdited || forceUpdate || portControl.pristine) {
      portControl.patchValue(matchingService.port, { emitEvent: false });
    }

    const hostControl = this.form.get('host');
    if (hostControl && (!hostControl.value || hostControl.pristine) && matchingService.frontendUrl) {
      hostControl.patchValue(this.stripHttpPrefix(matchingService.frontendUrl), { emitEvent: false });
    }
  }

  private stripHttpPrefix(url: string): string {
    return url.replace(/^(http:\/\/|https:\/\/)/, '');
  }
}
