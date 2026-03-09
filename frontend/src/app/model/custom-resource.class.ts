import { Node } from './node.class';

export class CustomResource extends Node {
  crdId: string | null;
  crdGroup: string;
  crdVersion: string;
  crdKind: string;
  crdPlural: string;
  crdScope: string;
  spec: Record<string, any>;

  constructor(
    id: string,
    name: string,
    crdId: string | null = null,
    crdGroup: string = '',
    crdVersion: string = '',
    crdKind: string = '',
    crdPlural: string = '',
    crdScope: string = 'Namespaced',
    spec: Record<string, any> = {}
  ) {
    super(id, name, 'cr');
    this.crdId = crdId;
    this.crdGroup = crdGroup;
    this.crdVersion = crdVersion;
    this.crdKind = crdKind;
    this.crdPlural = crdPlural;
    this.crdScope = crdScope;
    this.spec = spec;
  }
}
