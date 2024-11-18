export enum AssetType {
  PLAYBOOK = 'playbook',
  IMAGE = 'image',
  SCRIPT = 'script',
}

export class Asset {
    id!: string;
    name!: string;
    script?: string;
    type!: AssetType;
    port?: number;
    image?: string;
    version!: string;
  }

 