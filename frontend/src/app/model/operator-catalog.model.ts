export type OperatorInstallStatus =
  | 'NOT_INSTALLED'
  | 'INSTALLING'
  | 'INSTALLED'
  | 'UPGRADING'
  | 'UNINSTALLING'
  | 'DEGRADED';

export type OperatorUninstallPolicy = 'RETAIN_CRDS' | 'DELETE_CRDS_IF_EMPTY' | 'FORCE_DELETE';

export interface ManagedCrdRef {
  group: string;
  version: string;
  plural: string;
  namespaced: boolean;
}

export interface OperatorCatalogEntry {
  id: string;
  name: string;
  packageName: string;
  channel?: string;
  targetNamespace: string;
  desiredVersion: string;
  installedVersion?: string | null;
  uninstallPolicy: OperatorUninstallPolicy;
  status: OperatorInstallStatus;
  lastMessage?: string;
  updatedAt?: string;
  lastActionAt?: string;
  manifestYaml?: string;
  managedCrds: ManagedCrdRef[];
}

export interface OperatorCatalogPayload {
  name: string;
  packageName: string;
  channel?: string;
  targetNamespace: string;
  desiredVersion: string;
  uninstallPolicy: OperatorUninstallPolicy;
  manifestYaml?: string;
  managedCrds: ManagedCrdRef[];
}

export interface OperatorCatalogActionPayload {
  targetVersion?: string;
  force?: boolean;
}
