import { Node } from './node.class';

export class Deployment extends Node {
    replicas: number;
    strategyType: 'Recreate' | 'RollingUpdate';

    constructor(
        id: string,
        name: string,
        replicas: number = 1,
        strategyType: 'Recreate' | 'RollingUpdate' = 'RollingUpdate'
    ) {
        super(id, name, 'deployment');
        this.replicas = replicas;
        this.strategyType = strategyType;
    }
}