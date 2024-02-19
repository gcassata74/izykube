import { Node } from "./node.class";

export class Container extends Node{
    assetId!: string;

    constructor(id: string, name: string, kind: string) {
        super(id, name, kind);
    }

}
