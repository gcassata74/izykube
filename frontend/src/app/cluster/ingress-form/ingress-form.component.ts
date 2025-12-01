import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AutoSaveService } from '../../services/auto-save.service';
import { Node } from '../../model/node.class';
import { Ingress } from '../../model/ingress.class';
import { Service } from '../../model/service.class';
import { map, startWith } from 'rxjs/operators';

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
    if (changes['selectedNode'] && !changes['selectedNode'].firstChange) {
      this.refreshFormFromNode(changes['selectedNode'].currentValue as Ingress);
    }
  }

  private initForm(): void {
    const ingress = this.selectedNode as Ingress;
    this.ingressForm = this.fb.group({
      name: [ingress.name, Validators.required],
      host: [ingress.host, Validators.required],
      path: [ingress.path, Validators.required],
      serviceName: [ingress.serviceName || '', Validators.required],
      servicePort: [ingress.servicePort || 80, [Validators.required, Validators.min(1), Validators.max(65535)]],
      tls: [ingress.tls || ''],
      annotations: this.fb.array([])
    });
    this.initializeAnnotationsArray(ingress.annotations || {});

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
    const formValue$ = this.ingressForm.valueChanges.pipe(
      startWith(this.ingressForm.value),
      map(value => ({
        ...value,
        annotations: this.mapAnnotationsToRecord()
      }))
    );
    this.autoSaveService.enableAutoSave(this.ingressForm, this.selectedNode.id, formValue$);
  }

  private refreshFormFromNode(ingress: Ingress): void {
    if (!this.ingressForm) {
      return;
    }

    this.ingressForm.patchValue({
      name: ingress.name,
      host: ingress.host,
      path: ingress.path,
      serviceName: ingress.serviceName || '',
      servicePort: ingress.servicePort || 80,
      tls: ingress.tls || ''
    }, { emitEvent: false });

    this.setAnnotationsFromRecord(ingress.annotations || {});
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

  get annotationsArray(): FormArray {
    return (this.ingressForm?.get('annotations') as FormArray) || this.fb.array([]);
  }

  addAnnotation(entry: { key?: string; value?: string } = {}): void {
    this.annotationsArray.push(
      this.fb.group({
        key: [entry.key || ''],
        value: [entry.value || '']
      })
    );
  }

  removeAnnotation(index: number): void {
    this.annotationsArray.removeAt(index);
    if (!this.annotationsArray.length) {
      this.addAnnotation();
    }
  }

  trackByIndex(index: number): number {
    return index;
  }

  private initializeAnnotationsArray(annotations: Record<string, string>): void {
    const entries = Object.entries(annotations || {});
    if (!entries.length) {
      this.addAnnotation();
      return;
    }
    entries.forEach(([key, value]) => this.addAnnotation({ key, value }));
  }

  private setAnnotationsFromRecord(annotations: Record<string, string>): void {
    if (!this.ingressForm) {
      return;
    }

    const annotationsArray = this.ingressForm.get('annotations') as FormArray;
    while (annotationsArray.length) {
      annotationsArray.removeAt(0);
    }
    this.initializeAnnotationsArray(annotations);
  }

  private mapAnnotationsToRecord(): Record<string, string> {
    const record: Record<string, string> = {};
    this.annotationsArray.controls.forEach(control => {
      const key = (control.get('key')?.value || '').trim();
      if (!key) {
        return;
      }
      record[key] = control.get('value')?.value ?? '';
    });
    return record;
  }
}
