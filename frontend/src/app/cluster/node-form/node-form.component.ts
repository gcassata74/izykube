import { Component } from '@angular/core';
import { Observable, switchMap, map, of } from 'rxjs';
import { Asset } from '../../model/asset.class';
import { DataService } from 'src/app/services/data.service';
import { DiagramService } from 'src/app/services/diagram.service';
import { FormBuilder, FormGroup } from '@angular/forms';

@Component({
  selector: 'app-node-form',
  templateUrl: './node-form.component.html',
  styleUrls: ['./node-form.component.scss']
})
export class NodeFormComponent {
  selectedNodeType: string | null = null;
  nodeForm!: FormGroup;

  constructor(
    private fb: FormBuilder,
    private dataService: DataService,
    private diagramService: DiagramService
    
    ) {}

  ngOnInit(): void {
    this.nodeForm = this.fb.group({
      // Campi comuni o il formGroup può iniziare vuoto
    });

    this.diagramService.selectedNode$.subscribe(node => {
      this.selectedNodeType = node?.data?.type || null;
    });
  }

  // Logica per aggiungere dinamicamente componenti/form al nodeForm
  addChildForm(childForm: FormGroup): void {
    Object.keys(childForm.controls).forEach(key => {
      this.nodeForm.addControl(key, childForm.get(key));
    });
  }

}
