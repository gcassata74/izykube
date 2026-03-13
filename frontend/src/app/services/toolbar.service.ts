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

// toolbar.service.ts
import { BehaviorSubject, delay } from 'rxjs';
import { Button } from '../model/button.interface';
import { Injectable } from '@angular/core';


@Injectable({
  providedIn: 'root'
})
export class ToolbarService {
  private buttonsSource = new BehaviorSubject<Button[]>([]);
  buttons$ = this.buttonsSource.asObservable().pipe(
    delay(0)
  );

  setButtons(buttons: Button[]) {
    this.buttonsSource.next(buttons);
  }

  clearButtons() {
    this.buttonsSource.next([]);
  }
}

