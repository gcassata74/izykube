import { Node } from './node.class';

export type WorkloadType = 'DEPLOYMENT' | 'STATEFULSET' | 'DAEMONSET';

export class Deployment extends Node {
  replicas: number;
  strategyType: 'Recreate' | 'RollingUpdate';
  assetId: string;
  containerPort: number;
  serviceAccountRef?: string | null;
  serviceAccountName?: string | null;
  addToMesh?: boolean;
  override workloadType: WorkloadType;

  constructor(
    id: string,
    name: string,
    replicas: number = 1,
    strategyType: 'Recreate' | 'RollingUpdate' = 'RollingUpdate',
    assetId: string = '',
    containerPort: number = 80,
    workloadType: WorkloadType = 'DEPLOYMENT',
    serviceAccountRef: string | null = null,
    serviceAccountName: string | null = null,
    addToMesh: boolean = false
  ) {
    super(id, name, 'deployment');
    this.replicas = replicas;
    this.strategyType = strategyType;
    this.assetId = assetId;
    this.containerPort = containerPort;
    this.workloadType = workloadType;
    this.serviceAccountRef = serviceAccountRef;
    this.serviceAccountName = serviceAccountName;
    this.addToMesh = addToMesh;
  }
}
