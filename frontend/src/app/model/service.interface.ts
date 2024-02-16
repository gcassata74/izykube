import { Base } from "./base.interface";

export interface Service extends Base{
    kind: "service";
    name: string;
    type: 'ClusterIP' | 'NodePort' | 'LoadBalancer';
    ports: ServicePort[];
    selector?: { [key: string]: string };
  }
  
export interface ServicePort {
    port: number;
    targetPort: number;
    protocol: 'TCP' | 'UDP';
  }