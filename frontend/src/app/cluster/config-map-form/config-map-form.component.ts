import { Component, Input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Node } from '../../model/node.class';
import { ConfigMap } from '../../model/config-map.class';
import { AutoSaveService } from '../../services/auto-save.service';
import * as yaml from 'js-yaml';
import { EMPTY, filter, map, Observable, tap } from 'rxjs';

@Component({
  selector: 'app-config-map-form',
  templateUrl: './config-map-form.component.html',
  providers: [AutoSaveService]
})
export class ConfigMapFormComponent implements OnInit {
  @Input() selectedNode!: Node;
  form!: FormGroup;
  private lastValidYaml: string = '';

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService
  ) { }

  ngOnInit() {
    this.initForm();
    this.setupAutoSave();

  }

  private initForm() {
    const configMap = this.selectedNode as ConfigMap;

    this.form = this.fb.group({
      yaml: [configMap.yaml, Validators.required]
    });

  }


  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(
      this.form,
      this.selectedNode.id,
      this.form.valueChanges);
  }

}