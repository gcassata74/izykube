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
  type?: string;
}

export function generateManifestsFromBundle(bundle: ConfigBundle): GeneratedManifest[] {
  if (!bundle || !Array.isArray(bundle.entries) || bundle.entries.length === 0) {
    return [];
  }

  const manifests: GeneratedManifest[] = [];

  const plainEntries = bundle.entries.filter(entry => entry.sensitivity !== 'SECRET');
  const secretEntries = bundle.entries.filter(entry => entry.sensitivity === 'SECRET');

  const baseMetadata: Omit<GeneratedManifest['metadata'], 'name'> = {
    namespace: bundle.namespace,
    annotations: bundle.annotations && Object.keys(bundle.annotations).length
      ? bundle.annotations
      : undefined
  };

  if (plainEntries.length) {
    manifests.push(createConfigMapManifest(bundle.name, plainEntries, baseMetadata));
  }

  if (secretEntries.length) {
    manifests.push(createSecretManifest(bundle.name, secretEntries, baseMetadata));
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
    data: entries.reduce<Record<string, string>>((acc, entry) => {
      acc[entry.key] = encodeBase64(entry.value ?? '');
      return acc;
    }, {})
  };
}

function encodeBase64(value: string): string {
  const input = value ?? '';
  if (typeof btoa === 'function') {
    const bytes = new TextEncoder().encode(input);
    let binary = '';
    bytes.forEach((byte) => {
      binary += String.fromCharCode(byte);
    });
    return btoa(binary);
  }
  return input;
}
