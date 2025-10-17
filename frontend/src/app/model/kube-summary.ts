export interface PodSummary {
  name: string;
  namespace: string;
  status: string;
  ready: string;
  restarts: number;
  node: string;
  age: string;
}

export interface DeploymentSummary {
  name: string;
  namespace: string;
  readyReplicas: number;
  replicas: number;
  updatedReplicas: number;
  availableReplicas: number;
  age: string;
}

export interface ServiceSummary {
  name: string;
  namespace: string;
  type: string;
  clusterIp: string;
  externalIp: string;
  ports: string;
  age: string;
}

export interface NamespaceSummary {
  namespace: string;
  pods: PodSummary[];
  deployments: DeploymentSummary[];
  services: ServiceSummary[];
}

export interface NamespaceOption {
  name: string;
}
