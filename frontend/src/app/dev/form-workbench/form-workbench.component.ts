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

import { Component } from '@angular/core';
import { of } from 'rxjs';
import { ConfigBundle } from '../../model/config-bundle.model';
import { Node } from '../../model/node.class';
import { ClusterStatusEnum } from '../../cluster/enum/cluster.-status-enum';
import { DiagramService } from '../../services/diagram.service';
import { ConfigurationChangeService } from '../../services/configuration-change.service';
import { NotificationService } from '../../services/notification.service';
import { Store } from '@ngrx/store';

class DiagramServiceStub {
  updates: any[] = [];
  updateClusterNodes(nodeId: string, formValues: any): void {
    this.updates.push({ nodeId, formValues });
  }
  setSelectedNode(): void {}
  clearSelectedNode(): void {}
}

class StoreStub {
  select() {
    return of({ status: ClusterStatusEnum.CREATED });
  }
  dispatch(): void {}
}

class ConfigurationChangeServiceStub {
  emit(): void {}
}

class NotificationServiceStub {
  success(): void {}
  warn(): void {}
  error(): void {}
}

@Component({
  selector: 'app-form-workbench',
  templateUrl: './form-workbench.component.html',
  styleUrls: ['./form-workbench.component.scss'],
  providers: [
    { provide: DiagramService, useClass: DiagramServiceStub },
    { provide: Store, useClass: StoreStub },
    { provide: ConfigurationChangeService, useClass: ConfigurationChangeServiceStub },
    { provide: NotificationService, useClass: NotificationServiceStub }
  ]
})
export class FormWorkbenchComponent {
  configNode: Node & { configBundle: ConfigBundle } = {
    id: 'config-1',
    name: 'app-config',
    kind: 'configbundle',
    isAffected: false,
    configBundle: {
      id: 'config-1',
      name: 'app-config',
      namespace: 'default',
      annotations: {},
      entries: [{ key: '', value: '', sensitivity: 'PLAIN' }],
      showSecretsAsPlain: false
    }
  };
}
