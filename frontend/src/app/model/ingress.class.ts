import { Node } from "./node.class";

export class Ingress extends Node {
  host: string;
  path: string;
  servicePort: number;

  constructor(
    id: string,
    name: string,
    host: string,
    path: string,
    servicePort: number,
  ) {
    super(id, name, "ingress");
    this.host = host;
    this.path = path;
    this.servicePort = servicePort;
  }
}