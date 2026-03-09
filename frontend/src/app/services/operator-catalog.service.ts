import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  OperatorCatalogActionPayload,
  OperatorCatalogEntry,
  OperatorCatalogPayload,
} from '../model/operator-catalog.model';
import { DataService } from './data.service';

@Injectable({ providedIn: 'root' })
export class OperatorCatalogService {
  constructor(private dataService: DataService) {}

  list(): Observable<OperatorCatalogEntry[]> {
    return this.dataService.get<OperatorCatalogEntry[]>('/operator-catalog');
  }

  create(payload: OperatorCatalogPayload): Observable<OperatorCatalogEntry> {
    return this.dataService.post<OperatorCatalogEntry>('/operator-catalog', payload);
  }

  update(id: string, payload: OperatorCatalogPayload): Observable<OperatorCatalogEntry> {
    return this.dataService.put<OperatorCatalogEntry>(`/operator-catalog/${id}`, payload);
  }

  delete(id: string): Observable<{ message: string }> {
    return this.dataService.delete<{ message: string }>(`/operator-catalog/${id}`);
  }

  install(id: string, payload?: OperatorCatalogActionPayload): Observable<OperatorCatalogEntry> {
    return this.dataService.post<OperatorCatalogEntry>(`/operator-catalog/${id}/install`, payload || {});
  }

  upgrade(id: string, payload?: OperatorCatalogActionPayload): Observable<OperatorCatalogEntry> {
    return this.dataService.post<OperatorCatalogEntry>(`/operator-catalog/${id}/upgrade`, payload || {});
  }

  uninstall(id: string, payload?: OperatorCatalogActionPayload): Observable<OperatorCatalogEntry> {
    return this.dataService.post<OperatorCatalogEntry>(`/operator-catalog/${id}/uninstall`, payload || {});
  }
}
