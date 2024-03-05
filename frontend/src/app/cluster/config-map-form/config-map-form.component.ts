import { DiagramService } from './../../services/diagram.service';
import { distinctUntilChanged } from 'rxjs';
import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { ControlContainer, FormArray, FormBuilder, FormGroup, FormGroupDirective, Validators } from '@angular/forms';

@Component({
  selector: 'app-config-map-form',
  templateUrl: './config-map-form.component.html',
  styleUrls: ['./config-map-form.component.scss'],
  viewProviders: [{ provide: ControlContainer, useExisting: FormGroupDirective }]
})
export class ConfigMapFormComponent implements OnInit{

  form!: FormGroup;
  
  constructor(private fb: FormBuilder,
    private diagramService: DiagramService,
    private parentForm: FormGroupDirective) {}

  ngOnInit() {
    this.form = this.fb.group({
      entries: this.fb.array([])
    });

    this.diagramService.selectedNode$.subscribe((node:any) => {
      this.form.addControl('nodeId', this.fb.control(node?.data?.key));
      this.parentForm.form.addControl('configMapForm', this.form);
     });

    this.addEntry(); 
  }


  get entries(): FormArray {
    return this.form.get('entries') as FormArray;
  }

  newEntry(): FormGroup {
    return this.fb.group({
      key: ['', Validators.required],
      value: ['', Validators.required]
    });

  }

  addEntry(): void {
    this.entries.push(this.newEntry());
  }

  removeEntry(index: number): void {
    this.entries.removeAt(index);
    // Check if all entries have been removed
    if (this.entries.length === 0) {
      this.addEntry(); // Add an empty entry if the last one is removed
    }
  }


}
