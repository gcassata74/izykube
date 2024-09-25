import { Node } from "./node.class";

export type VolumeType = 'emptyDir' | 'hostPath' | 'persistentVolumeClaim' | 'configMap' | 'secret';

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

export interface SecretVolumeConfig extends BaseVolumeConfig {
  type: 'secret';
  secretName: string;
  items?: { key: string; path: string }[];
}

export type VolumeConfig = 
  | EmptyDirVolumeConfig 
  | HostPathVolumeConfig 
  | PersistentVolumeClaimVolumeConfig 
  | SecretVolumeConfig;

export class Volume extends Node {
    config: VolumeConfig;

    constructor(id: string, name: string, config: VolumeConfig) {
        super(id, name, "volume");
        this.config = config;
    }
}