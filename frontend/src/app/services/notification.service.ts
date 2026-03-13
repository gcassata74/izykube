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
import { MessageService } from 'primeng/api';

@Injectable({
  providedIn: 'root'
})
export class NotificationService{

  constructor(private messageService: MessageService) {}

  success(summary: string, detail?: string) {
    this.messageService.clear();
    this.messageService.add({severity:'success', summary, detail});
  }

  info(summary: string, detail?: string) {
    this.messageService.clear();
    this.messageService.add({severity:'info', summary, detail});
  }

  warn(summary: string, detail?: string) {
    this.messageService.clear();
    this.messageService.add({severity:'warn', summary, detail});
  }

  error(summary: string, detail?: string) {
    this.messageService.clear();
    this.messageService.add({severity:'error', summary, detail, sticky: true});
  }
}
