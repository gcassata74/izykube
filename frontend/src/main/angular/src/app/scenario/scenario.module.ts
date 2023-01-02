import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DiagramEditorComponent } from './diagram-editor/diagram-editor.component';
import {IconService} from './services/icon.service';



@NgModule({
  declarations: [
    DiagramEditorComponent
  ],
  exports: [
    DiagramEditorComponent
  ],
  imports: [
    CommonModule
  ],
  providers: [
    IconService
  ]
})
export class ScenarioModule { }
