import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DiagramComponent } from './diagram/diagram.component';
import { PaletteComponent } from './palette/palette.component';



@NgModule({
  declarations: [
    DiagramComponent,
    PaletteComponent
  ],
  exports: [
    DiagramComponent
  ],
  imports: [
    CommonModule
  ]
})
export class ScenarioModule { }
