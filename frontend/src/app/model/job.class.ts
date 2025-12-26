import { config } from "rxjs";
import { Node } from "./node.class";

  
  export class Job extends Node {
      assetId!: string;
      serviceAccountRef?: string | null;
  
      constructor(id: string, name: string, assetId: string, serviceAccountRef: string | null = null) {
          super(id, name, "job");
          this.assetId = assetId;
          this.serviceAccountRef = serviceAccountRef;
      }
  }
