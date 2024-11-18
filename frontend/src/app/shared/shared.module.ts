import { NgModule } from '@angular/core';
import { EditorModule } from 'primeng/editor';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { YamlDirective } from './directives/yaml.directive';
import { BashDirective } from './directives/bash.directive';

@NgModule({
  declarations: [
    YamlDirective,
    BashDirective
  ],
  imports: [
    BrowserAnimationsModule,
    ReactiveFormsModule,
    BrowserModule,
    FormsModule
  ],
  exports: [
    YamlDirective,
    BashDirective
  ]
})
export class SharedModule { }
