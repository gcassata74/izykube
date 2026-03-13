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
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class DataService {


  private baseUrl = '/api'; // Replace with your API base URL

  constructor(private http: HttpClient) { }

  private buildUrl(endpoint: string): string {
    const sanitizedEndpoint = endpoint.startsWith('/') ? endpoint.substring(1) : endpoint;
    return `${this.baseUrl}/${sanitizedEndpoint}`;
  }

  // Generic GET method
  get<T>(endpoint: string, params?: HttpParams): Observable<T> {
    const url = this.buildUrl(endpoint);
    return this.http.get<T>(url, { params });
  }

  getText(endpoint: string, params?: HttpParams): Observable<string> {
    const url = this.buildUrl(endpoint);
    return this.http.get(url, { params, responseType: 'text' });
  }

  // Generic POST method
  post<T>(endpoint: string, data: any): Observable<T> {
    const url = this.buildUrl(endpoint);
    return this.http.post<T>(url, data);
  }

  postBlob(endpoint: string, data: any): Observable<HttpResponse<Blob>> {
    const url = this.buildUrl(endpoint);
    return this.http.post(url, data, { observe: 'response', responseType: 'blob' });
  }

  // Generic PUT method
  put<T>(endpoint: string, data: any): Observable<T> {
    const url = this.buildUrl(endpoint);
    return this.http.put<T>(url, data);
  }

  patch<T>(endpoint: string, data: any): Observable<T> {
    const url = this.buildUrl(endpoint);
    return this.http.patch<T>(url, data);
  }

  // Generic DELETE method
  delete<T>(endpoint: string): Observable<T> {
    const url = this.buildUrl(endpoint);
    return this.http.delete<T>(url);
  }
}
