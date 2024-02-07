export class ClusterNode {
    id!: string;
    name!: string;

    constructor(node: go.Node) {
        this.id = node.data.key;
        this.name = node.data.name;
    }
}

export class Link {
    id!: string;
    source!: string;
    target!: string;

    constructor(node: go.Node) {
        this.id = node.data.key;
        this.source = node.data.source;
        this.target = node.data.target;
    }
}

export class Container extends ClusterNode {
    assetId!: string;
    type!: string; // initContainer, sidecar, appContainer

    constructor(node: go.Node) {
        super(node);
        this.assetId = node.data.assetId;
        this.type = node.data.type;
    }
}

export class Pod extends ClusterNode {
    assetId!: string;

    constructor(node: go.Node) {
        super(node);
        this.assetId = node.data.assetId;
        // Assuming containers are an array of container data within node.data
        // and you need to create Container instances for each
        // this.containers = node.data.containers.map(containerData => new Container(containerData));
    }
}

export class Ingress extends ClusterNode {
    port!: number;
    service!: string;
    path!: string;

    constructor(node: go.Node) {
        super(node);
        this.port = node.data.port;
        this.service = node.data.service;
        this.path = node.data.path;
    }
}

export class Service extends ClusterNode {
    port!: number;
    serviceType!: string; // LoadBalancer, ClusterIP, NodePort

    constructor(node: go.Node) {
        super(node);
        this.port = node.data.port;
        this.serviceType = node.data.serviceType;
    }
}

export class Deployment extends ClusterNode {
    replicas!: number;
    containers!: Container[];

    constructor(node: go.Node) {
        super(node);
        //this.replicas = node.data.replicas;
        // Assuming containers are an array of container data within node.data
        // and you need to create Container instances for each
        // this.containers = node.data.containers.map(containerData => new Container(containerData));
    }
}


export class ConfigMap extends ClusterNode {
    data!: any;

    constructor(node: go.Node) {
        super(node);
      //  this.data = node.data.data;
    }
}
