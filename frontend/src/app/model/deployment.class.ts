import { Node } from './node.class';

export class Deployment extends Node {
    replicas: number;
    assetId: string;
    containerPort: number;
    resources: {
        cpu: string;
        memory: string;
    };

    constructor(
        id: string,
        name: string,
        replicas: number,
        assetId: string,
        containerPort: number,
        resources: { cpu: string; memory: string } = { cpu: '', memory: '' },
        envVars: { name: string; value: string }[] = []
    ) {
        super(id, name, 'deployment');
        this.replicas = replicas;
        this.assetId = assetId;
        this.containerPort = containerPort;
        this.resources = resources;
    }

 
    updateResources(cpu: string, memory: string) {
        this.resources = { cpu, memory };
    }
}