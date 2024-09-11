import { AutoSaveService } from './../../services/auto-save.service';
import { DiagramService } from './../../services/diagram.service';
import { Component, Input, OnInit, Output } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Node } from '../../model/node.class';
import { ConfigMap } from '../../model/config-map.class';

@Component({
  selector: 'app-config-map-form',
  templateUrl: './config-map-form.component.html',
  styleUrls: ['./config-map-form.component.scss'],
  providers: [AutoSaveService]
})
export class ConfigMapFormComponent implements OnInit {


  form!: FormGroup;
  @Input() selectedNode!: Node;

  constructor(private fb: FormBuilder,
    private autoSaveService: AutoSaveService,
    private diagramService: DiagramService) { }

  get entries(): FormArray {
    return this.form.get('entries') as FormArray;
  }


  ngOnInit() {
    this.form = this.fb.group({
      entries: this.fb.array([])
    });

    this.initializeForm();
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }


  initializeForm() {

    const configMap = this.selectedNode as ConfigMap
    //if we have entries in the state then add them to the form
    if (Object.entries(configMap.entries).length > 0) {

      Object.entries(configMap.entries).forEach(([key, value]: [string, any]) => {
        this.addEntry(value.key, value.value);
      });

    } else {
      //add empty entry
      this.addEntry();
    }
  }


  newEntry(key: string, value: string): FormGroup {

    return this.fb.group({
      key: [key, Validators.required],
      value: [value, Validators.required]
    });

  }

  addEntry(key?: string, value?: string): void {
    this.entries.push(this.newEntry(key ? key : '', value ? value : ''));
  }

  removeEntry(index: number): void {
    this.entries.removeAt(index);
    // Check if all entries have been removed
    if (this.entries.length === 0) {
      this.addEntry();
    }
  }

}
