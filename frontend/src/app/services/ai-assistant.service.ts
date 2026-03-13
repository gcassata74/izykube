/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
