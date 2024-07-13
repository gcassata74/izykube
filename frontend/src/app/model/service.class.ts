import { Node } from './node.class';

export class Service extends Node {
    type: ServiceType;
    port: number;
    nodePort?: number;

    constructor(
        id: string,
        name: string,
        type: ServiceType,
        port: number,
        nodePort?: number
    ) {
        super(id, name, 'service');
        this.type = type;
        this.port = port;
        this.nodePort = nodePort;
    }

    // You can keep or modify these methods as needed
    setNodePort(port: number) {
        if (this.type === 'NodePort') {
            this.nodePort = port;
        }
    }

    changeType(newType: ServiceType) {
        this.type = newType;
        if (newType !== 'NodePort') {
            this.nodePort = undefined;
        }
    }
}

export type ServiceType = 'ClusterIP' | 'NodePort' | 'LoadBalancer' | 'ExternalName';