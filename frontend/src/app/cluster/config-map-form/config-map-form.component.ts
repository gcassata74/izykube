import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-config-map-form',
  templateUrl: './config-map-form.component.html',
  styleUrls: ['./config-map-form.component.scss']
})
export class ConfigMapFormComponent implements OnInit{

  @Output() formReady = new EventEmitter<FormGroup>();
  configMapForm: FormGroup;
  
  constructor(private fb: FormBuilder) {
    this.configMapForm = this.fb.group({
      entries: this.fb.array([])
    });
  }

  ngOnInit() {
    this.addEntry(); 
    this.formReady.emit(this.configMapForm); 
  }


  get entries(): FormArray {
    return this.configMapForm.get('entries') as FormArray;
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
