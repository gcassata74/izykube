import { Base } from "./base.interface";
import { EnvVarSource } from "./env-var-source.interface";
import { VolumeMount } from "./volume.interface";

export interface Container extends Base{
    image: string;
    ports?: ContainerPort[];
    env?: EnvironmentVariable[];
    volumeMounts?: VolumeMount[];
  }
  
export interface ContainerPort {
    containerPort: number;
    protocol?: 'TCP' | 'UDP';
  }


  export interface EnvironmentVariable {
    name: string;
    value?: string;
    valueFrom?: EnvVarSource;
  }
  
