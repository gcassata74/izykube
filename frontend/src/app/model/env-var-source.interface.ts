export interface EnvVarSource {
    fieldRef?: FieldRef;
    secretKeyRef?: SecretKeyRef;
    configMapKeyRef?: ConfigMapKeyRef;
    resourceFieldRef?: ResourceFieldRef;
  }
  
  export interface FieldRef {
    apiVersion?: string; // Defaults to "v1"
    fieldPath: string;
  }
  
  export interface SecretKeyRef {
    name: string;
    key: string;
    optional?: boolean;
  }
  
  export interface ConfigMapKeyRef {
    name: string;
    key: string;
    optional?: boolean;
  }
  
  export interface ResourceFieldRef {
    containerName?: string;
    resource: string;
    divisor?: string; // Defaults to "1"
  }
  