import { DiagramService } from './../../services/diagram.service';
import { DiagramComponent } from './../../diagram/diagram.component';
import { Component, EventEmitter, Output } from '@angular/core';
import { FormGroup, FormBuilder, Validators, ControlContainer, FormGroupDirective } from '@angular/forms';
import { SelectItem } from 'primeng/api/selectitem';
import { Observable } from 'rxjs';
import { AssetService } from 'src/app/services/asset.service';
import { Node } from '../../model/node.class';

@Component({
  selector: 'app-deployment-form',
  templateUrl: './deployment-form.component.html',
  styleUrls: ['./deployment-form.component.scss'],
  viewProviders: [{ provide: ControlContainer, useExisting: FormGroupDirective }]
})
export class DeploymentFormComponent {
  form!: FormGroup;
  filteredAssets$!: Observable<any[] | undefined >;
  selectedNodeType: string | null = null;
  selectedNodeKey!: string;

  constructor(
    private diagramService: DiagramService,
    private fb: FormBuilder,
    private assetService: AssetService,
    private parentForm: FormGroupDirective
    ) {}

  ngOnInit(): void {

    this.form = this.fb.group({
      replicas: ['', Validators.required],
    });

    this.diagramService.selectedNode$.subscribe((node: any) => {
      this.selectedNodeType = node?.data?.type || null;
      this.selectedNodeKey = node?.data?.key;
      this.filteredAssets$ = this.assetService.getFilterdAssets(node);
      this.form.addControl('id', this.fb.control( this.selectedNodeKey));
      this.parentForm.form.addControl('deploymentForm', this.form);
    });
  }
}
