import { Node } from './../../model/node.class';
import { Deployment } from './../../model/deployment.class';
import { ConfigMap } from './../../model/config-map';
import { Store, select } from '@ngrx/store';
import { Component, AfterViewInit } from '@angular/core';
import { Observable, switchMap, map, of, filter } from 'rxjs';
import { Asset } from '../../model/asset.class';
import { DataService } from 'src/app/services/data.service';
import { DiagramService } from 'src/app/services/diagram.service';
import { FormBuilder, FormGroup } from '@angular/forms';
import { getNodeById } from 'src/app/store/selectors/selectors';

@Component({
  selector: 'app-node-form',
  templateUrl: './node-form.component.html',
  styleUrls: ['./node-form.component.scss']
})
export class NodeFormComponent {

  selectedNodeType!: string;
  selectedNodeId!: string;
  nodeForm!: FormGroup;

  constructor(
    private fb: FormBuilder,
    private dataService: DataService,
    private store: Store,
    private diagramService: DiagramService

  ) { }

  ngOnInit(): void {
    this.nodeForm = this.fb.group({
    });

    this.diagramService.selectedNode$.subscribe(node => {
      this.selectedNodeType = node?.data?.type || null;
      this.selectedNodeId = node?.data?.key;
    });

  }
  

  updateClusterNodes() {
    const formValue = {};
  
    // Iterate over each control in the nodeForm
    Object.keys(this.nodeForm.controls).forEach(key => {
      const subForm = this.nodeForm.get(key) as FormGroup; // Get the subform
  
      // Check if the subform has a 'nodeId' control and if its value matches this.selectedNodeId
      if (subForm.controls['id'] && subForm.controls['id'].value === this.selectedNodeId) {
        // Include this subform's values in the formValue object
        Object.assign(formValue, subForm.value);
      }
    });
  
    // formValue now contains the values from the subforms where 'nodeId' matches this.selectedNodeId
    this.diagramService.updateClusterNodes(this.selectedNodeId, formValue);
  }

}
