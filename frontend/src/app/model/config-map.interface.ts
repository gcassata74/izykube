import { Base } from "./base.interface";

export interface ConfigMap extends Base{
    data: { [key: string]: string };
  }
  