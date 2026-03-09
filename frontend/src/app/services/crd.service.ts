import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CrdDefinition, CrdDefinitionSummary } from '../model/crd-definition.class';
import { DataService } from './data.service';

@Injectable({ providedIn: 'root' })
export class CrdService {
  constructor(private dataService: DataService) {}

  list(): Observable<CrdDefinitionSummary[]> {
    return this.dataService.get<CrdDefinitionSummary[]>('/crds');
  }

  listAvailable(): Observable<CrdDefinitionSummary[]> {
    return this.dataService.get<CrdDefinitionSummary[]>('/crds/available');
  }

  get(id: string): Observable<CrdDefinition> {
    return this.dataService.get<CrdDefinition>(`/crds/${id}`);
  }

  getAvailable(id: string): Observable<CrdDefinition> {
    return this.dataService.get<CrdDefinition>(`/crds/available/${encodeURIComponent(id)}`);
  }

  create(payload: Partial<CrdDefinition>): Observable<CrdDefinition> {
    return this.dataService.post<CrdDefinition>('/crds', payload);
  }

  update(id: string, payload: Partial<CrdDefinition>): Observable<CrdDefinition> {
    return this.dataService.put<CrdDefinition>(`/crds/${id}`, payload);
  }

  delete(id: string): Observable<any> {
    return this.dataService.delete<any>(`/crds/${id}`);
  }
}
