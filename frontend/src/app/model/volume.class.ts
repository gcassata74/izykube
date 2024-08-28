import { Node } from "./node.class";

export type VolumeType = 'emptyDir' | 'hostPath' | 'configMap' | 'secret' | 'persistentVolumeClaim';

export interface VolumeConfig {
  type: VolumeType;
  [key: string]: any;  // Additional properties based on volume type
}

export class Volume extends Node {
    config: VolumeConfig;

    constructor(id: string, name: string, config: VolumeConfig) {
        super(id, name, "volume");
        this.config = config;
    }
}