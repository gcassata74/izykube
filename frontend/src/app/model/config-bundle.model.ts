export type ConfigEntrySensitivity = 'PLAIN' | 'SECRET';

export interface ConfigEntry {
  key: string;
  value: string;
  sensitivity: ConfigEntrySensitivity;
}

export interface ConfigBundle {
  id: string;
  name: string;
  namespace: string;
  annotations?: Record<string, string>;
  entries: ConfigEntry[];
  showSecretsAsPlain?: boolean;
}

export interface ConfigBundleMeta {
  hasPlainEntries: boolean;
  hasSecretEntries: boolean;
  entryCount: number;
}

export function ensureConfigBundleDefaults(
  partial: Partial<ConfigBundle> & { id: string; name: string }
): ConfigBundle {
  return {
    id: partial.id,
    name: partial.name,
    namespace: partial.namespace || 'default',
    annotations: { ...(partial.annotations || {}) },
    entries: Array.isArray(partial.entries)
      ? partial.entries.map(entry => ({
          key: entry.key ?? '',
          value: entry.value ?? '',
          sensitivity: entry.sensitivity === 'SECRET' ? 'SECRET' : 'PLAIN'
        }))
      : [],
    showSecretsAsPlain: partial.showSecretsAsPlain ?? false
  };

}
