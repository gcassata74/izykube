export enum AssetType {
  PLAYBOOK = 'playbook',
  IMAGE = 'image',
  SCRIPT = 'script',
}

export class Asset {
    id!: string;
    name!: AssetType;
    yaml?: string;
    type!: string;
    port!: number;
    image!: string;
    version!: string;
  }

 