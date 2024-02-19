import { Node } from "./node.class";

export class Service extends Node{

    constructor(id: string, name: string, kind: string) {
        super(id, name, "service");
    }
}
