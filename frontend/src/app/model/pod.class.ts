import { Node } from "./node.class";

export class Pod extends Node{

    assetId!: string;

    constructor(id: string, name: string, kind: string) {
        super(id, name, "pod");
    }

}
