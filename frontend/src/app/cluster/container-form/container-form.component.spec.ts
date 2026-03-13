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

import { FormBuilder } from '@angular/forms';
import { of } from 'rxjs';
import { ContainerFormComponent } from './container-form.component';
import { Container } from 'src/app/model/container.class';
import { AutoSaveService } from 'src/app/services/auto-save.service';
import { AssetService } from 'src/app/services/asset.service';
import { NotificationService } from 'src/app/services/notification.service';

describe('ContainerFormComponent', () => {
  let component: ContainerFormComponent;
  let autoSaveService: jasmine.SpyObj<AutoSaveService>;
  let assetService: jasmine.SpyObj<AssetService>;
  let notificationService: jasmine.SpyObj<NotificationService>;

  beforeEach(() => {
    autoSaveService = jasmine.createSpyObj('AutoSaveService', ['enableAutoSave']);
    assetService = jasmine.createSpyObj('AssetService', ['getAssets', 'getImageAssets']);
    assetService.getAssets.and.returnValue(of([]));
    assetService.getImageAssets.and.returnValue(of([]));
    notificationService = jasmine.createSpyObj('NotificationService', ['error']);

    component = new ContainerFormComponent(
      new FormBuilder(),
      autoSaveService,
      assetService,
      notificationService
    );
  });

  function initWithNode(node: Container) {
    component.selectedNode = node;
    component.ngOnInit();
  }

  it('defaults role form control to null when the value is missing', () => {
    const legacyNode = {
      id: 'c1',
      name: 'legacy',
      kind: 'container',
      assetId: '',
      containerPort: 8080
    } as Container;

    initWithNode(legacyNode);

    expect(component.form.get('role')?.value).toBeNull();
  });

  it('preserves the stored role value when present', () => {
    const node = new Container('c2', 'sidecar', 'asset', 8080, 'SIDECAR');

    initWithNode(node);

    expect(component.form.get('role')?.value).toBe('SIDECAR');
  });
});
