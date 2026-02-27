import { Node } from './node.class';

export class Service extends Node {
    type: ServiceType;
    port: number;
    nodePort?: number;
    exposeService: boolean;
    frontendUrl?: string;
    forwardEnabled?: boolean;
    forwardPort?: number;
    forwardTargetPort?: number;
    forwardActive?: boolean;

    constructor(
        id: string,
        name: string,
        type: ServiceType,
        port: number,
        exposeService: boolean = false,
        frontendUrl?: string,
        nodePort?: number,
        forwardEnabled: boolean = false,
        forwardPort?: number,
        forwardTargetPort?: number,
        forwardActive: boolean = false
    ) {
        super(id, name, 'service');
        this.type = type;
        this.port = port;
        this.nodePort = nodePort;
        this.exposeService = exposeService;
        this.frontendUrl = frontendUrl;
        this.forwardEnabled = forwardEnabled;
        this.forwardPort = forwardPort;
        this.forwardTargetPort = forwardTargetPort;
        this.forwardActive = forwardActive;
    }

    // Existing methods...

    setExposeService(expose: boolean) {
        this.exposeService = expose;
        if (!expose) {
            this.frontendUrl = undefined;
        }
    }

    setFrontendUrl(url: string) {
        if (this.exposeService) {
            this.frontendUrl = url;
        }
    }
}

export type ServiceType = 'ClusterIP' | 'NodePort' | 'LoadBalancer' | 'ExternalName';
