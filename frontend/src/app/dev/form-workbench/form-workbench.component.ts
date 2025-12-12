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
