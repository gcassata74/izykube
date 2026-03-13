/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
