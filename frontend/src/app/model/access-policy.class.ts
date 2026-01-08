import { Node } from './node.class';

export type AccessPolicyBindingStrategy =
  | 'WORKLOAD_SA_PER_WORKLOAD'
  | 'WORKLOAD_SA_PER_POLICY'
  | 'WORKLOAD_SA_EXPLICIT_REFERENCE';

export interface AccessPolicyRule {
  apiGroups: string[];
  resources: string[];
  verbs: string[];
  resourceNames?: string[];
}

export class AccessPolicy extends Node {
  namespace: string;
  rules: AccessPolicyRule[];
  targetBindingStrategy: AccessPolicyBindingStrategy;
  existingServiceAccountName?: string | null;

  constructor(
    id: string,
    name: string,
    namespace: string = 'default',
    rules: AccessPolicyRule[] = [],
    targetBindingStrategy: AccessPolicyBindingStrategy = 'WORKLOAD_SA_PER_WORKLOAD',
    existingServiceAccountName: string | null = null
  ) {
    super(id, name, 'accesspolicy');
    this.namespace = namespace;
    this.rules = rules;
    this.targetBindingStrategy = targetBindingStrategy;
    this.existingServiceAccountName = existingServiceAccountName;
  }
}
