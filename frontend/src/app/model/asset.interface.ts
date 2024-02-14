import { EnvVarSource } from './env-var-source.interface';
import { ofType } from '@ngrx/effects';
export interface Asset {
    id: string;
    label: string;
    port: number;
    image: string;
    type: string;
    version: string;
  }