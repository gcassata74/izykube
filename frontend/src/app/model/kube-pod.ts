export interface KubeObjectMeta {
  name?: string;
  namespace?: string;
  creationTimestamp?: string;
}

export interface KubeContainer {
  name?: string;
  image?: string;
}

export interface KubePodSpec {
  containers?: KubeContainer[];
}

export interface KubeContainerStateTerminated {
  reason?: string;
  exitCode?: number;
  startedAt?: string;
  finishedAt?: string;
  message?: string;
}

export interface KubeContainerStateWaiting {
  reason?: string;
  message?: string;
}

export interface KubeContainerStateRunning {
  startedAt?: string;
}

export interface KubeContainerState {
  running?: KubeContainerStateRunning;
  waiting?: KubeContainerStateWaiting;
  terminated?: KubeContainerStateTerminated;
}

export interface KubeContainerStatus {
  name?: string;
  ready?: boolean;
  restartCount?: number;
  state?: KubeContainerState;
  lastState?: KubeContainerState;
}

export interface KubePodStatus {
  phase?: string;
  containerStatuses?: KubeContainerStatus[];
}

export interface KubePod {
  metadata?: KubeObjectMeta;
  spec?: KubePodSpec;
  status?: KubePodStatus;
}

