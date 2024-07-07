import { Node } from './node.class';

export class Service extends Node {
    type: ServiceType;
    port: number;
    targetPort: number;
    protocol: Protocol;
    nodePort?: number;

    constructor(
        id: string,
        name: string,
        type: ServiceType,
        port: number,
        targetPort: number,
        protocol: Protocol = 'TCP',
        nodePort?: number
    ) {
        super(id, name, 'service');
        this.type = type;
        this.port = port;
        this.targetPort = targetPort;
        this.protocol = protocol;
        this.nodePort = nodePort;
    }

    // You can add methods here if needed, for example:
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
export type Protocol = 'TCP' | 'UDP';