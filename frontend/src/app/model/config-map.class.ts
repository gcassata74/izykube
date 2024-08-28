import { config } from "rxjs";
import { Node } from "./node.class";

export interface ConfigMapEntry {
    key: string;
    value: string;
  }
  
  export class ConfigMap extends Node {
      entries: ConfigMapEntry[];
  
      constructor(id: string, name: string, entries: ConfigMapEntry[] = []) {
          super(id, name, "configmap");
          this.entries = entries;
      }
  }