import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PersistentVolume } from '../model/persistent-volume.class';
import { DataService } from './data.service';

@Injectable({
  providedIn: 'root'
})
export class PersistentVolumeService {

  constructor(private dataService: DataService) {}

  getVolumes(): Observable<PersistentVolume[]> {
    return this.dataService.get<PersistentVolume[]>('/persistent-volumes');
  }

  getVolume(name: string): Observable<PersistentVolume> {
    return this.dataService.get<PersistentVolume>(`/persistent-volumes/${name}`);
  }

  createVolume(request: PersistentVolume): Observable<PersistentVolume> {
    return this.dataService.post<PersistentVolume>('/persistent-volumes', request);
  }

  updateVolume(name: string, request: PersistentVolume): Observable<PersistentVolume> {
    return this.dataService.put<PersistentVolume>(`/persistent-volumes/${name}`, request);
  }

  deleteVolume(name: string): Observable<void> {
    return this.dataService.delete<void>(`/persistent-volumes/${name}`);
  }
}
