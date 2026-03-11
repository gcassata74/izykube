import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { YamlDirective } from './directives/yaml.directive';
import { BashDirective } from './directives/bash.directive';
import { LogHeaderComponent } from './log-header/log-header.component';
import { DropdownModule } from 'primeng/dropdown';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';

@NgModule({
  declarations: [
    YamlDirective,
    BashDirective,
    LogHeaderComponent
  ],
  imports: [
    BrowserAnimationsModule,
    ReactiveFormsModule,
    BrowserModule,
    FormsModule,
    DropdownModule,
    ButtonModule,
    InputTextModule
  ],
  exports: [
    YamlDirective,
    BashDirective,
    LogHeaderComponent,
    DropdownModule,
    ButtonModule,
    InputTextModule,
    FormsModule,
    ReactiveFormsModule
  ]
})
export class SharedModule { }
