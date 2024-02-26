import { DiagramService } from './../../services/diagram.service';
import { DiagramComponent } from './../../diagram/diagram.component';
import { Component, EventEmitter, Output } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { SelectItem } from 'primeng/api/selectitem';
import { Observable } from 'rxjs';
import { AssetService } from 'src/app/services/asset.service';

@Component({
  selector: 'app-deployment-form',
  templateUrl: './deployment-form.component.html',
  styleUrls: ['./deployment-form.component.scss']
})
export class DeploymentFormComponent {
  @Output() formReady = new EventEmitter<FormGroup>();
  deploymentForm!: FormGroup;
  filteredAssets$!: Observable<any[] | undefined >;
  selectedNodeType: string | null = null;
  selectedNodeKey!: string;

  constructor(
    private diagramService: DiagramService,
    private fb: FormBuilder,
    private assetService: AssetService
    ) {}

  ngOnInit(): void {
    this.diagramService.selectedNode$.subscribe(node => {
      this.selectedNodeType = node?.data?.type || null;
      this.selectedNodeKey = node?.data?.key;
      this.filteredAssets$ = this.assetService.getFilterdAssets(node);
    });

    this.deploymentForm = this.fb.group({
      replicas: ['', Validators.required],
    
    });

    this.formReady.emit(this.deploymentForm); 
  }

}
