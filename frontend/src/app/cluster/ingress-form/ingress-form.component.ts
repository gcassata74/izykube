import { Component, Input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AutoSaveService } from '../../services/auto-save.service';
import { Node } from '../../model/node.class';
import { Ingress } from '../../model/ingress.class';
import { Observable } from 'rxjs';
import { AssetService } from '../../services/asset.service';

@Component({
  selector: 'app-ingress-form',
  templateUrl: './ingress-form.component.html',
  styleUrls: ['./ingress-form.component.scss'],
  providers: [AutoSaveService]
})
export class IngressFormComponent implements OnInit {
  @Input() selectedNode!: Node;
  ingressForm!: FormGroup;
  filteredAssets$!: Observable<any[]>;

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService,
    private assetService: AssetService
  ) {}

  ngOnInit() {
    this.initForm();
    this.setupAutoSave();
    this.loadAssets();
  }

  private initForm() {
    const ingress = this.selectedNode as Ingress;
    this.ingressForm = this.fb.group({
      name: [ingress.name, Validators.required],
      host: [ingress.host, Validators.required],
      path: [ingress.path, Validators.required],
      servicePort: [ingress.servicePort, Validators.required]
    });
  }

  private loadAssets() {
    this.filteredAssets$ = this.assetService.getAssets();
  }

  emitChange(event: any) {
    // Handle the asset selection change event
    console.log('Selected asset:', event.value);
    // You might want to update other form fields based on the selected asset
  }

  onSubmit() {
    if (this.ingressForm.valid) {
      // Handle form submission logic here
      console.log(this.ingressForm.value);
    }
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.ingressForm, this.selectedNode.id, this.ingressForm.valueChanges);
  }
}