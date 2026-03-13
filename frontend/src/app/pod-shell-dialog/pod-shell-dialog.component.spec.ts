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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DialogModule } from 'primeng/dialog';
import { PodShellDialogComponent } from './pod-shell-dialog.component';
import { PodShellService } from '../services/pod-shell.service';

class MockPodShellService {
  createShellSocket(): WebSocket {
    return {
      readyState: WebSocket.CLOSED,
      send: () => {},
      close: () => {}
    } as unknown as WebSocket;
  }
}

describe('PodShellDialogComponent', () => {
  let component: PodShellDialogComponent;
  let fixture: ComponentFixture<PodShellDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DialogModule],
      declarations: [PodShellDialogComponent],
      providers: [{ provide: PodShellService, useClass: MockPodShellService }]
    }).compileComponents();

    fixture = TestBed.createComponent(PodShellDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should emit closed when dialog hides', () => {
    const closedSpy = spyOn(component.closed, 'emit');
    const visibleSpy = spyOn(component.visibleChange, 'emit');
    component.onDialogVisibleChange(false);
    expect(visibleSpy).toHaveBeenCalledWith(false);
    expect(closedSpy).toHaveBeenCalled();
  });
});
