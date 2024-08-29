import { Node } from './node.class';

export class Deployment extends Node {
    replicas: number;
    strategy: DeploymentStrategy;
    minReadySeconds?: number;
    revisionHistoryLimit?: number;
    progressDeadlineSeconds?: number;

    constructor(
        id: string,
        name: string,
        replicas: number = 1,
        strategy: DeploymentStrategy = { type: 'RollingUpdate' }
    ) {
        super(id, name, 'deployment');
        this.replicas = replicas;
        this.strategy = strategy;
    }
}

export interface DeploymentStrategy {
    type: 'Recreate' | 'RollingUpdate';
    rollingUpdate?: {
        maxUnavailable?: number | string;
        maxSurge?: number | string;
    };
}