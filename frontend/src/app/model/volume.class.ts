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

import { Node } from "./node.class";

export type VolumeType = 'emptyDir' | 'hostPath' | 'persistentVolumeClaim' | 'configMap' | 'secret';

export interface VolumeItem {
  key: string;
  path: string;
  mode?: string;
}

interface BaseVolumeConfig {
  type: VolumeType;
  mountPath: string;
}

export interface EmptyDirVolumeConfig extends BaseVolumeConfig {
  type: 'emptyDir';
  medium?: string;
  sizeLimit?: string;
}

export interface HostPathVolumeConfig extends BaseVolumeConfig {
  type: 'hostPath';
  path: string;
  hostPathType?: string;
}

export interface PersistentVolumeClaimVolumeConfig extends BaseVolumeConfig {
  type: 'persistentVolumeClaim';
  claimName: string;
  readOnly?: boolean;
}

export interface ConfigMapVolumeConfig extends BaseVolumeConfig {
  type: 'configMap';
  name: string;
  optional?: boolean;
  items?: VolumeItem[];
}

export interface SecretVolumeConfig extends BaseVolumeConfig {
  type: 'secret';
  secretName: string;
  optional?: boolean;
  items?: VolumeItem[];
}

export type VolumeConfig = 
  | EmptyDirVolumeConfig 
  | HostPathVolumeConfig 
  | PersistentVolumeClaimVolumeConfig 
  | ConfigMapVolumeConfig
  | SecretVolumeConfig;

export class Volume extends Node {
    config: VolumeConfig;

    constructor(id: string, name: string, config: VolumeConfig) {
        super(id, name, "volume");
        this.config = config;
    }
}
