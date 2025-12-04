import { ConfigBundle, ConfigEntry } from '../model/config-bundle.model';

export interface GeneratedManifest {
  apiVersion: string;
  kind: 'ConfigMap' | 'Secret';
  metadata: {
    name: string;
    namespace: string;
    annotations?: Record<string, string>;
  };
  data?: Record<string, string>;
  stringData?: Record<string, string>;
  type?: string;
}

export function generateManifestsFromBundle(bundle: ConfigBundle): GeneratedManifest[] {
  if (!bundle || !Array.isArray(bundle.entries) || bundle.entries.length === 0) {
    return [];
  }

  const plainEntries = bundle.entries.filter(entry => entry.sensitivity !== 'SECRET');
  const secretEntries = bundle.entries.filter(entry => entry.sensitivity === 'SECRET');
  const manifests: GeneratedManifest[] = [];

  if (plainEntries.length === 0 && secretEntries.length === 0) {
    return [];
  }

  const baseMetadata: Omit<GeneratedManifest['metadata'], 'name'> = {
    namespace: bundle.namespace,
    annotations: bundle.annotations && Object.keys(bundle.annotations).length
      ? bundle.annotations
      : undefined
  };

  if (plainEntries.length && !secretEntries.length) {
    manifests.push(createConfigMapManifest(bundle.name, plainEntries, baseMetadata));
    return manifests;
  }

  if (secretEntries.length && !plainEntries.length) {
    manifests.push(createSecretManifest(bundle.name, secretEntries, baseMetadata));
    return manifests;
  }

  if (plainEntries.length && secretEntries.length) {
    manifests.push(createConfigMapManifest(`${bundle.name}-config`, plainEntries, baseMetadata));
    manifests.push(createSecretManifest(`${bundle.name}-secret`, secretEntries, baseMetadata));
  }

  return manifests;
}

function createConfigMapManifest(
  name: string,
  entries: ConfigEntry[],
  metadataExtras: Omit<GeneratedManifest['metadata'], 'name'>
): GeneratedManifest {
  return {
    apiVersion: 'v1',
    kind: 'ConfigMap',
    metadata: {
      name,
      namespace: metadataExtras.namespace,
      ...(metadataExtras.annotations ? { annotations: metadataExtras.annotations } : {})
    },
    data: entries.reduce<Record<string, string>>((acc, entry) => {
      acc[entry.key] = entry.value ?? '';
      return acc;
    }, {})
  };
}

function createSecretManifest(
  name: string,
  entries: ConfigEntry[],
  metadataExtras: Omit<GeneratedManifest['metadata'], 'name'>
): GeneratedManifest {
  return {
    apiVersion: 'v1',
    kind: 'Secret',
    metadata: {
      name,
      namespace: metadataExtras.namespace,
      ...(metadataExtras.annotations ? { annotations: metadataExtras.annotations } : {})
    },
    type: 'Opaque',
    stringData: entries.reduce<Record<string, string>>((acc, entry) => {
      acc[entry.key] = entry.value ?? '';
      return acc;
    }, {})
  };
}
