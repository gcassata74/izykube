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

export interface IngressSummary {
  name: string;
  namespace: string;
  hosts: string;
  serviceTargets: string;
  ingressClassName: string;
  path: string;
  tls: string;
  age: string;
}

export interface IngressGatewayInfo {
  host: string;
  httpPort?: number;
  httpsPort?: number;
  loadBalancer: boolean;
}

export interface IngressClassSummary {
  name: string;
  controller: string;
}

export interface ConfigMapSummary {
  name: string;
  namespace: string;
  dataEntries: number;
  age: string;
}

export interface SecretSummary {
  name: string;
  namespace: string;
  type: string;
  dataEntries: number;
  age: string;
}

export interface JobSummary {
  name: string;
  namespace: string;
  completions?: number;
  succeeded?: number;
  failed?: number;
  active?: number;
  age: string;
}

export interface CronJobSummary {
  name: string;
  namespace: string;
  schedule: string;
  suspended: boolean;
  lastScheduleTime: string;
  activeJobs: number;
  age: string;
}

export interface DaemonSetSummary {
  name: string;
  namespace: string;
  desired?: number;
  current?: number;
  ready?: number;
  available?: number;
  updated?: number;
  age: string;
}

export interface StatefulSetSummary {
  name: string;
  namespace: string;
  readyReplicas?: number;
  replicas?: number;
  updatedReplicas?: number;
  age: string;
}

export interface NamespaceSummary {
  namespace: string;
  pods: PodSummary[];
  deployments: DeploymentSummary[];
  services: ServiceSummary[];
  ingresses: IngressSummary[];
  configMaps: ConfigMapSummary[];
  secrets: SecretSummary[];
  jobs: JobSummary[];
  cronJobs: CronJobSummary[];
  daemonSets: DaemonSetSummary[];
  statefulSets: StatefulSetSummary[];
}

export interface NamespaceOption {
  name: string;
}

export interface PodLogs {
  name: string;
  namespace: string;
  logs: string;
}

export interface DeploymentLogs {
  name: string;
  namespace: string;
  pods: PodLogs[];
}
