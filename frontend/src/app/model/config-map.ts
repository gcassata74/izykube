import { config } from "rxjs";
import { Node } from "./node.class";

export class ConfigMap extends Node{
    entries: { [key: string]: string };

    constructor(id: string, name: string, kind: string) {
        super(id, name, "configMap");
        this.entries = {};
    }
  
}
