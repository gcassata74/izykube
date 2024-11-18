import { AfterViewInit, Directive, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { AbstractControl, ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors } from '@angular/forms';
import * as ace from "ace-builds";

@Directive({
  selector: '[appBash]',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: BashDirective,
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: BashDirective,
      multi: true
    }
  ]
})
export class BashDirective implements OnInit, ControlValueAccessor, OnDestroy {
  private editor: any;
  
  @Input() theme: string = 'ace/theme/monokai'; // Dark theme better for code
  @Input() mode: string = 'ace/mode/sh'; // Bash mode
  @Input() readOnly: boolean = false;
  @Input() height: string = '400px';
  @Input() width: string = '100%';
  @Input() tabSize: number = 2;
  @Input() showGutter: boolean = true;
  @Input() showPrintMargin: boolean = false;
  
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
    this.initializeEditor();
    this.setupEventListeners();
  }

  private initializeEditor(): void {
    this.editor.setTheme(this.theme);
    this.editor.session.setMode(this.mode);
    this.editor.setReadOnly(this.readOnly || this.isDisabled);
    
    // Additional Bash-specific configurations
    this.editor.session.setTabSize(this.tabSize);
    this.editor.renderer.setShowGutter(this.showGutter);
    this.editor.setShowPrintMargin(this.showPrintMargin);
    
    // Enable auto-completion
    this.editor.setOptions({
      enableBasicAutocompletion: true,
      enableLiveAutocompletion: true,
      enableSnippets: true
    });

    // Add common Bash keywords for highlighting
    const bashKeywords = [
      'if', 'then', 'else', 'elif', 'fi', 'case', 'esac', 'for', 'while', 
      'do', 'done', 'in', 'function', 'return', 'exit', 'export', 'source'
    ];
    
    this.editor.session.$mode.$highlightRules.addKeywords(bashKeywords);
  }

  private setupEventListeners(): void {
    this.editor.on('change', () => {
      const newValue = this.editor.getValue();
      this.onChange(newValue);
      this.onTouched();
    });

    // Add custom key bindings
    this.editor.commands.addCommand({
      name: 'saveFile',
      bindKey: { win: 'Ctrl-S', mac: 'Command-S' },
      exec: () => {
        this.onTouched();
      }
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

    // Basic Bash syntax validation
    try {
      const currentContent = this.editor.getValue();
      if (this.hasBasicSyntaxErrors(currentContent)) {
        return {
          bashError: {
            message: 'Basic syntax error detected'
          }
        };
      }
      return null;
    } catch (e: any) {
      return {
        bashError: {
          message: 'Invalid Bash script'
        }
      };
    }
  }

  private hasBasicSyntaxErrors(content: string): boolean {
    // Implement basic syntax checking
    const lines = content.split('\n');
    let openIf = 0;
    let openCase = 0;
    let openFor = 0;
    let openWhile = 0;

    for (const line of lines) {
      const trimmedLine = line.trim();
      
      // Check for unclosed if statements
      if (trimmedLine.startsWith('if ')) openIf++;
      if (trimmedLine === 'fi') openIf--;
      
      // Check for unclosed case statements
      if (trimmedLine.startsWith('case ')) openCase++;
      if (trimmedLine === 'esac') openCase--;
      
      // Check for unclosed for loops
      if (trimmedLine.startsWith('for ')) openFor++;
      if (trimmedLine === 'done' && openFor > 0) openFor--;
      
      // Check for unclosed while loops
      if (trimmedLine.startsWith('while ')) openWhile++;
      if (trimmedLine === 'done' && openWhile > 0) openWhile--;
    }

    return openIf !== 0 || openCase !== 0 || openFor !== 0 || openWhile !== 0;
  }

  ngOnDestroy(): void {
    if (this.editor) {
      this.editor.destroy();
      this.editor = null;
    }
  }
}