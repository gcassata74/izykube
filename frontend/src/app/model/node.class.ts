export interface Node {
    id: string;
    name: string;
    type: string;
}

export interface Link {
    id: string;
    source: string;
    target: string;
}

export interface Container extends Node {
    assetId: string;
}

export interface Pod extends Node {
    assetId: string;
    containers: Container[];
}



export interface Ingress extends Node {
    port: number;
    service: string;
    path: string;
}

export interface Service extends Node {
    port: number;
    type: string;
}