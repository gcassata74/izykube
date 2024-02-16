import { Base } from "./base.interface";

export interface Volume extends Base{
    kind: "volume";
    name: string;
    persistentVolumeClaim?: {
      claimName: string;
    };
    configMap?: {
      name: string;
      items?: { key: string; path: string }[];
    };
  }  
 
  export interface VolumeMount{
    mountPath: string;
  }
   