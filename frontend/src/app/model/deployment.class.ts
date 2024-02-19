import { Asset } from './asset.class';
import { Node } from "./node.class";

export class Deployment extends Node{

    assetId!: string;


    constructor(id: string, name: string, kind: string) {
        super(id, name, "deployment");
    }
}



