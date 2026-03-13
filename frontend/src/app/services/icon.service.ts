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

@Injectable({
  providedIn: 'root'
})
export class IconService {
  private icons: { [key: string]: string } = {};

  // Define a configuration object with icon names and file paths
  private iconConfig = {
    'ingress': 'pi pi-globe',
    'service': 'pi pi-share-alt',
    'configmap': 'pi pi-clone',
    'configbundle': 'pi pi-clone',
    'secret': 'pi pi-lock',
    'container': 'pi pi-box',
    'deployment': 'pi pi-sitemap',
    'accesspolicy': 'pi pi-shield',
    'rolebinding': 'pi pi-link',
    'serviceaccount': 'pi pi-id-card',
    'volume': 'pi pi-database',
    'job': 'pi pi-refresh',
    'cr': 'pi pi-sliders-h',
    'istio': 'assets/images/diagram/istio.svg'
  };

  constructor() {
    // Initialize the icons map with the configuration object
    this.icons = { ...this.iconConfig };
  }

  // Retrieve the file path for a given image name
  getIconPath(name: string): string {
    return this.icons[name] || '';
  }
}
