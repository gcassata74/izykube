import { Base } from "./base.interface";

export interface ConfigMap extends Base{
    kind: "configMap";
    data: { [key: string]: string };
  }
  