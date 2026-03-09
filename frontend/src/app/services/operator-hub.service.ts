import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpParams } from '@angular/common/http';
import { DataService } from './data.service';
import { OperatorHubListResponse } from '../model/operator-hub.model';

@Injectable({ providedIn: 'root' })
export class OperatorHubService {
  constructor(private dataService: DataService) {}

  list(query?: string, page = 1, size = 50): Observable<OperatorHubListResponse> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));
    if (query && query.trim().length) {
      params = params.set('q', query.trim());
    }
    return this.dataService.get<OperatorHubListResponse>('/operatorhub/operators', params);
  }

  fetchInstallYaml(name: string): Observable<string> {
    return this.dataService.getText(`/operatorhub/install/${encodeURIComponent(name)}`);
  }
}
