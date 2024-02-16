import { Base } from "./base.interface";
import { PodSpec } from "./pod.interface";

export interface Deployment extends Base {
    kind: "deployment";
    replicas: number;
    selector: Selector;
    template: PodTemplate;
  }
  
  export interface Selector {
    matchLabels?: { [key: string]: string };
    matchExpressions?: SelectorExpression[];
  }
  
  export interface SelectorExpression {
    key: string;
    operator: 'In' | 'NotIn' | 'Exists' | 'DoesNotExist';
    values?: string[];
  }
  
  export interface PodTemplate {
    metadata: { labels: { [key: string]: string } };
    spec: PodSpec;
  }
    