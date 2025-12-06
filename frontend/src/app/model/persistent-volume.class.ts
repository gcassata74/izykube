export interface PersistentVolume {
  name: string;
  storageClassName?: string;
  capacity?: string;
  accessModes?: string[];
  reclaimPolicy?: string;
  volumeMode?: string;
  path?: string;
}
