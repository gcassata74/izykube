import { config } from "rxjs";
import { Node } from "./node.class";

  
  export class Job extends Node {
      assetId!: string;
  
      constructor(id: string, name: string, assetId: string) {
          super(id, name, "job");
          this.assetId = assetId;
      }
  }