import { config } from "rxjs";
import { Node } from "./node.class";

  
  export class ConfigMap extends Node {
      yaml!: string;
  
      constructor(id: string, name: string, yaml: string) {
          super(id, name, "configmap");
          this.yaml = yaml;
      }
  }