import { AfterViewInit, Directive, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { AbstractControl, ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors } from '@angular/forms';
import * as ace from "ace-builds";
import * as yaml from 'js-yaml';

@Directive({
  selector: '[appYaml]',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: YamlDirective,
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: YamlDirective,
      multi: true
    }
  ]
})
export class YamlDirective implements OnInit, ControlValueAccessor, OnDestroy{

  private editor: any;

  @Input() theme: string = 'ace/theme/clouds';
  @Input() mode: string = 'ace/mode/yaml';
  @Input() readOnly: boolean = false;
  @Input() height: string = '400px';
  @Input() width: string = '100%';
  @Input() set content(value: string) {
    if (this.editor && value !== this.editor.getValue()) {
      this.editor.setValue(value, 1);
    }
  }

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};
  private isDisabled: boolean = false;

  constructor(private el: ElementRef) {}


  ngOnInit(): void {
    const element = this.el.nativeElement;
    element.style.height = this.height;
    element.style.width = this.width;

    ace.config.set("fontSize", "14px");
    ace.config.set(
      "basePath",
      "https://unpkg.com/ace-builds@1.4.12/src-noconflict"
    );

    this.editor = ace.edit(this.el.nativeElement);
    this.editor.setTheme(this.theme);
    this.editor.session.setMode(this.mode);
    this.editor.setReadOnly(this.readOnly);

    // Emit content change
    this.editor.on('change', () => {
      const newValue = this.editor.getValue();
      this.onChange(newValue);
      this.onTouched();
    });
  }

  writeValue(value: string): void {
    if (this.editor && value !== this.editor.getValue()) {
      this.editor.setValue(value || '', 1);
    }
  }

    registerOnChange(fn: (value: string) => void): void {
      this.onChange = fn;
    }

    registerOnTouched(fn: () => void): void {
      this.onTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
      this.isDisabled = isDisabled;
      if (this.editor) {
        this.editor.setReadOnly(isDisabled);
      }
    }

    validate(control: AbstractControl): ValidationErrors | null {
      if (!control.value) {
        return null;
      }

      try {
        const currentContent = this.editor.getValue();
        yaml.load(control.value);
        return null;
      } catch (e: any) {
      if (e instanceof yaml.YAMLException) {
          return {
            yamlError: {
              line: e.mark?.line,
              column: e.mark?.column,
              reason: e.reason,
              message: e.message
            }
          };
        }
        return { yamlError: { message: 'Invalid YAML' } };
      }

    }

  ngOnDestroy(): void {
    if (this.editor) {
      this.editor.destroy();
      this.editor = null;
    }
  }





}
