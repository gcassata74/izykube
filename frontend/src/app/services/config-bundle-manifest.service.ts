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

  const manifests: GeneratedManifest[] = [];

  const baseMetadata: Omit<GeneratedManifest['metadata'], 'name'> = {
    namespace: bundle.namespace,
    annotations: bundle.annotations && Object.keys(bundle.annotations).length
      ? bundle.annotations
      : undefined
  };

  const hasSecretEntries = bundle.entries.some(entry => entry.sensitivity === 'SECRET');
  manifests.push(
    hasSecretEntries
      ? createSecretManifest(bundle.name, bundle.entries, baseMetadata)
      : createConfigMapManifest(bundle.name, bundle.entries, baseMetadata)
  );

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
