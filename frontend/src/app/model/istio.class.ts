import { Node } from './node.class';

export class Istio extends Node {
  host: string;
  path: string;
  serviceName: string;
  servicePort: number;

  constructor(
    id: string,
    name: string,
    host: string,
    path: string,
    serviceName: string,
    servicePort: number,
  ) {
    super(id, name, 'istio');
    this.host = host;
    this.path = path;
    this.serviceName = serviceName;
    this.servicePort = servicePort;
  }
}
