import { Component, Input } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { Container } from 'src/app/model/container.class';
import { AssetService } from 'src/app/services/asset.service';
import { AutoSaveService } from 'src/app/services/auto-save.service';

@Component({
  selector: 'app-container-form',
  templateUrl: './container-form.component.html',
  styleUrls: ['./container-form.component.scss'],
  providers: [AutoSaveService]
})
export class ContainerFormComponent {
  @Input() selectedNode!: Container;
  form!: FormGroup;
  assets$!: Observable<any[]>;

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
    this.form = this.fb.group({
      name: [this.selectedNode.name, Validators.required],
      assetId: [this.selectedNode.assetId, Validators.required],
      containerPort: [this.selectedNode.containerPort, [Validators.required, Validators.min(1)]]
    });
  }

  private loadAssets() {
    this.assets$ = this.assetService.getAssets();
  }

  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }
}
