import { Node } from './node.class';

export class Deployment extends Node {
    replicas: number;
    assetId: string;
    containerPort: number;
    resources: {
        cpu: string;
        memory: string;
    };
    envVars: { name: string; value: string }[];

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
        this.envVars = envVars;
    }

    // You can add methods here if needed, for example:
    addEnvVar(name: string, value: string) {
        this.envVars.push({ name, value });
    }

    removeEnvVar(index: number) {
        this.envVars.splice(index, 1);
    }

    updateResources(cpu: string, memory: string) {
        this.resources = { cpu, memory };
    }
}