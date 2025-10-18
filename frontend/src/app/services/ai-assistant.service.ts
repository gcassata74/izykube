import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
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
}
