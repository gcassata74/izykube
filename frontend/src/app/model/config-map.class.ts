import { Node } from "./node.class";

export class ConfigMap extends Node {
  yaml!: string;
  secret: boolean;

  constructor(
    id: string,
    name: string,
    yaml: string,
    kind: 'configmap' | 'secret' = 'configmap',
    secret?: boolean
  ) {
    super(id, name, kind);
    this.yaml = yaml;
    this.secret = secret ?? kind === 'secret';
  }
}
