import { Base } from "./base.interface";

export interface Ingress extends Base {
    kind: "ingress";
    name: string;
    annotations?: { [key: string]: string };
    rules: IngressRule[];
  }
  
  interface IngressRule {
    host: string;
    http: {
      paths: IngressPath[];
    };
  }
  
  interface IngressPath {
    path: string;
    backend: {
      serviceName: string;
      servicePort: number;
    };
  }