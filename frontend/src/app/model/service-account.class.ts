import { Node } from './node.class';

export class ServiceAccount extends Node {
  namespace: string;
  automountServiceAccountToken: boolean;
  labels: Record<string, string>;
  annotations: Record<string, string>;
  rbacProfile: 'NONE' | 'VIEW' | 'EDIT' | 'ADMIN';

  constructor(
    id: string,
    name: string,
    namespace: string = 'default',
    automountServiceAccountToken: boolean = true,
    labels: Record<string, string> = {},
    annotations: Record<string, string> = {},
    rbacProfile: 'NONE' | 'VIEW' | 'EDIT' | 'ADMIN' = 'NONE'
  ) {
    super(id, name, 'serviceaccount');
    this.namespace = namespace;
    this.automountServiceAccountToken = automountServiceAccountToken;
    this.labels = labels;
    this.annotations = annotations;
    this.rbacProfile = rbacProfile;
  }
}
