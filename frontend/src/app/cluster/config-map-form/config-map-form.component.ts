import { Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Node } from '../../model/node.class';
import { ConfigMap } from '../../model/config-map.class';
import { AutoSaveService } from '../../services/auto-save.service';
import { NotificationService } from '../../services/notification.service';
import { MenuItem } from 'primeng/api';
import * as yaml from 'js-yaml';
import { AiAssistantService } from '../../services/ai-assistant.service';

@Component({
  selector: 'app-config-map-form',
  templateUrl: './config-map-form.component.html',
  providers: [AutoSaveService]
})
export class ConfigMapFormComponent implements OnInit {
  @Input() selectedNode!: Node;
  form!: FormGroup;

  @ViewChild('yamlInput', { static: false }) yamlInput!: ElementRef<HTMLInputElement>;
  @ViewChild('jsonInput', { static: false }) jsonInput!: ElementRef<HTMLInputElement>;
  importMenuItems: MenuItem[] = [];
  exportMenuItems: MenuItem[] = [];
  aiDialogVisible = false;
  aiPrompt = '';
  aiLoading = false;

  get isSecretNode(): boolean {
    const node = this.selectedNode as ConfigMap;
    return node?.kind === 'secret' || (node as any)?.secret === true;
  }

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService,
    private notificationService: NotificationService,
    private aiAssistantService: AiAssistantService
  ) {}

  ngOnInit() {
    this.initForm();
    this.setupAutoSave();
    this.setupMenus();
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

  private setupMenus(): void {
    this.importMenuItems = [
      { label: 'Import YAML', icon: 'pi pi-upload', command: () => this.triggerYamlImport() },
      { label: 'Import JSON', icon: 'pi pi-upload', command: () => this.triggerJsonImport() }
    ];

    this.exportMenuItems = [
      { label: 'Export YAML', icon: 'pi pi-download', command: () => this.exportYaml() },
      { label: 'Export JSON', icon: 'pi pi-download', command: () => this.exportJson() }
    ];
  }

  triggerYamlImport(): void {
    this.yamlInput?.nativeElement.click();
  }

  triggerJsonImport(): void {
    this.jsonInput?.nativeElement.click();
  }

  onFileSelected(event: Event, format: 'yaml' | 'json'): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) {
      return;
    }

    const file = input.files[0];
    const reader = new FileReader();

    reader.onload = () => {
      try {
        const content = (reader.result ?? '') as string;
        let yamlContent = content;

        if (format === 'json') {
          const parsedJson = JSON.parse(content);
          yamlContent = yaml.dump(parsedJson, { lineWidth: -1 });
        } else {
          yaml.load(content); // validate YAML
        }

        this.setYamlContent(yamlContent);
        this.notificationService.success('ConfigMap imported', `Loaded ${format.toUpperCase()} file.`);
      } catch (error: any) {
        const detail = error?.message || 'Unable to process the selected file.';
        this.notificationService.error('Import failed', detail);
      } finally {
        input.value = '';
      }
    };

    reader.onerror = () => {
      this.notificationService.error('Import failed', 'Unable to read the selected file.');
      input.value = '';
    };

    reader.readAsText(file);
  }

  exportYaml(): void {
    const yamlValue = this.form.get('yaml')?.value;
    if (!yamlValue) {
      this.notificationService.warn('Nothing to export', 'ConfigMap YAML is empty.');
      return;
    }

    this.downloadFile(yamlValue, this.buildFileName('yaml'), 'application/x-yaml');
  }

  exportJson(): void {
    const yamlValue = this.form.get('yaml')?.value;
    if (!yamlValue) {
      this.notificationService.warn('Nothing to export', 'ConfigMap YAML is empty.');
      return;
    }

    try {
      const parsedYaml = yaml.load(yamlValue);
      const jsonValue = JSON.stringify(parsedYaml, null, 2);
      this.downloadFile(jsonValue, this.buildFileName('json'), 'application/json');
    } catch (error: any) {
      const detail = error?.message || 'Current YAML cannot be parsed.';
      this.notificationService.error('Export failed', detail);
    }
  }

  private setYamlContent(content: string): void {
    this.form.patchValue({ yaml: content });
    const control = this.form.get('yaml');
    control?.markAsDirty();
    control?.markAsTouched();
    control?.updateValueAndValidity();
  }

  private downloadFile(content: string, fileName: string, mimeType: string): void {
    try {
      const blob = new Blob([content], { type: mimeType });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = fileName;
      link.click();
      URL.revokeObjectURL(link.href);
    } catch (error: any) {
      const detail = error?.message || 'Unable to generate download.';
      this.notificationService.error('Download failed', detail);
    }
  }

  private buildFileName(extension: string): string {
    const baseName = (this.selectedNode?.name || 'configmap')
      .trim()
      .replace(/\s+/g, '-')
      .toLowerCase();
    return `${baseName}.${extension}`;
  }

  openAiDialog(): void {
    this.aiPrompt = '';
    this.aiDialogVisible = true;
  }

  closeAiDialog(): void {
    if (this.aiLoading) {
      return;
    }
    this.aiDialogVisible = false;
  }

  submitAiPrompt(): void {
    if (!this.aiPrompt || !this.aiPrompt.trim()) {
      this.notificationService.warn('Add an instruction', 'Describe what you want the ConfigMap to include.');
      return;
    }

    this.aiLoading = true;

    this.aiAssistantService.generate({
      task: 'configmap_yaml',
      prompt: this.aiPrompt.trim(),
      context: this.form.get('yaml')?.value
    }).subscribe({
      next: (response) => {
        this.setYamlContent(response.content);
        this.notificationService.success('ConfigMap generated', 'YAML updated from local AI.');
        this.aiDialogVisible = false;
        this.aiLoading = false;
      },
      error: (error) => {
        const detail = error?.error || error?.message || 'Local AI request failed.';
        this.notificationService.error('Generation failed', detail);
        this.aiLoading = false;
      }
    });
  }
}
