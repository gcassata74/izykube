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
