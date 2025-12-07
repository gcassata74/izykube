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
    expect(manifests).toHaveLength(1);
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
    expect(manifests).toHaveLength(1);
    const manifest = manifests[0];
    expect(manifest.kind).toBe('Secret');
    expect(manifest.metadata.name).toBe('secrets-only');
    expect(manifest.stringData).toEqual({ DB_PASSWORD: 's3cret' });
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
    expect(manifests).toHaveLength(1);

    const secret = manifests[0] as GeneratedManifest;

    expect(secret.kind).toBe('Secret');
    expect(secret.metadata.name).toBe('mixed');
    expect(secret.metadata.annotations).toEqual({ 'izylife.io/managed': 'true' });
    expect(secret.stringData).toEqual({
      APP_MODE: 'prod',
      DB_PASSWORD: 's3cret'
    });
  });
});
