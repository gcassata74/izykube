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

import { Node } from './node.class';
import { ConfigBundle, ensureConfigBundleDefaults } from './config-bundle.model';

export class ConfigBundleNode extends Node {
  configBundle: ConfigBundle;

  constructor(
    id: string,
    name: string,
    initial: Partial<ConfigBundle> = {},
    kind: 'configmap' | 'secret' | 'configbundle' = 'configbundle'
  ) {
    super(id, name, kind);
    this.configBundle = ensureConfigBundleDefaults({
      id,
      name,
      ...initial
    });
  }
}
