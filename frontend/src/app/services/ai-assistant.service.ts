import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { DataService } from './data.service';

export interface AiGenerateRequest {
  task: string;
  prompt: string;
  context?: string;
  format?: string;
}

export interface AiGenerateResponse {
  content: string;
  task: string;
  format?: string;
  model?: string;
}

export interface AiChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
  timestamp?: string;
}

export interface AiChatRequest {
  task: string;
  messages: AiChatMessage[];
  context?: string;
  model?: string;
}

export interface AiChatResponse {
  messages: AiChatMessage[];
  model?: string;
  task?: string;
}

export interface AiImportYamlRequest {
  yaml: string;
  name?: string;
}

export interface AiImportYamlResponse {
  id?: string;
  name: string;
  nodes: any[];
  links: any[];
  diagram?: string;
  nameSpace?: string;
}

export interface AiExportYamlResponse {
  yaml: string;
}

export interface AiHelmChartExportResponse {
  blob: Blob;
  fileName: string;
}

@Injectable({
  providedIn: 'root'
})
export class AiAssistantService {

  constructor(private dataService: DataService) {}

  generate(request: AiGenerateRequest): Observable<AiGenerateResponse> {
    return this.dataService.post<AiGenerateResponse>('ai/generate', request);
  }

  chat(request: AiChatRequest): Observable<AiChatResponse> {
    return this.dataService.post<AiChatResponse>('ai/chat', request);
  }

  importYaml(request: AiImportYamlRequest): Observable<AiImportYamlResponse> {
    return this.dataService.post<AiImportYamlResponse>('ai/import-yaml', request);
  }

  exportYaml(cluster: any): Observable<AiExportYamlResponse> {
    const payload = { ...cluster, exportMode: 'FLAT_YAML' };
    return this.dataService.post<AiExportYamlResponse>('ai/export-yaml', payload);
  }

  exportHelmChart(cluster: any): Observable<AiHelmChartExportResponse> {
    const payload = { ...cluster, exportMode: 'HELM_CHART' };
    return this.dataService.postBlob('ai/export-yaml', payload).pipe(
      map(response => ({
        blob: response.body ?? new Blob(),
        fileName: this.extractFileName(response.headers.get('content-disposition')) || 'cluster-chart.zip'
      }))
    );
  }

  private extractFileName(dispositionHeader: string | null): string | null {
    if (!dispositionHeader) {
      return null;
    }
    const fileNameMatch = /filename\*?=(?:UTF-8''|")?([^\";]+)/i.exec(dispositionHeader);
    if (!fileNameMatch || !fileNameMatch[1]) {
      return null;
    }
    try {
      return decodeURIComponent(fileNameMatch[1].replace(/\"/g, '').trim());
    } catch {
      return fileNameMatch[1].replace(/\"/g, '').trim();
    }
  }
}
