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

import { ConfigBundle } from '../model/config-bundle.model';
import { GeneratedManifest, generateManifestsFromBundle } from './config-bundle-manifest.service';

function buildBundle(partial: Partial<ConfigBundle>): ConfigBundle {
  return {
    id: partial.id || 'bundle-1',
    name: partial.name || 'app-config',
    namespace: partial.namespace || 'default',
    annotations: partial.annotations,
    entries: partial.entries || [],
    showSecretsAsPlain: partial.showSecretsAsPlain ?? false
  };
}

describe('generateManifestsFromBundle', () => {
  it('returns empty array when there are no entries', () => {
    const bundle = buildBundle({ entries: [] });
    const manifests = generateManifestsFromBundle(bundle);
    expect(manifests).toEqual([]);
  });

  it('creates a ConfigMap when only plain entries are provided', () => {
    const bundle = buildBundle({
      name: 'plain-only',
      entries: [
        { key: 'APP_MODE', value: 'prod', sensitivity: 'PLAIN' },
        { key: 'LOG_LEVEL', value: 'info', sensitivity: 'PLAIN' }
      ]
    });

    const manifests = generateManifestsFromBundle(bundle);
    expect(manifests.length).toBe(1);
    const manifest = manifests[0];
    expect(manifest.kind).toBe('ConfigMap');
    expect(manifest.metadata.name).toBe('plain-only');
    expect(manifest.data).toEqual({
      APP_MODE: 'prod',
      LOG_LEVEL: 'info'
    });
  });

  it('creates a Secret when only secret entries are provided', () => {
    const bundle = buildBundle({
      name: 'secrets-only',
      entries: [
        { key: 'DB_PASSWORD', value: 's3cret', sensitivity: 'SECRET' }
      ]
    });

    const manifests = generateManifestsFromBundle(bundle);
    expect(manifests.length).toBe(1);
    const manifest = manifests[0];
    expect(manifest.kind).toBe('Secret');
    expect(manifest.metadata.name).toBe('secrets-only');
    expect(manifest.data).toEqual({ DB_PASSWORD: 'czNjcmV0' });
    expect(manifest.type).toBe('Opaque');
  });

  it('creates both ConfigMap and Secret when entries are mixed', () => {
    const bundle = buildBundle({
      name: 'mixed',
      annotations: { 'izylife.io/managed': 'true' },
      entries: [
        { key: 'APP_MODE', value: 'prod', sensitivity: 'PLAIN' },
        { key: 'DB_PASSWORD', value: 's3cret', sensitivity: 'SECRET' }
      ]
    });

    const manifests = generateManifestsFromBundle(bundle);
    expect(manifests.length).toBe(2);

    const configMap = manifests.find(m => m.kind === 'ConfigMap') as GeneratedManifest;
    const secret = manifests.find(m => m.kind === 'Secret') as GeneratedManifest;

    expect(configMap).toBeTruthy();
    expect(secret).toBeTruthy();

    expect(configMap.metadata.name).toBe('mixed');
    expect(configMap.metadata.annotations).toEqual({ 'izylife.io/managed': 'true' });
    expect(configMap.data).toEqual({
      APP_MODE: 'prod'
    });

    expect(secret.metadata.name).toBe('mixed');
    expect(secret.metadata.annotations).toEqual({ 'izylife.io/managed': 'true' });
    expect(secret.data).toEqual({
      DB_PASSWORD: 'czNjcmV0'
    });
  });
});
