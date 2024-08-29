import { Node } from "./node.class";

export class Container extends Node{
    assetId: string;
    containerPort: number;

    constructor(id: string, name: string, assetId: string, containerPort: number) {
        super(id, name, 'container');
        this.assetId = assetId;
        this.containerPort = containerPort;
    }

}
