import { Cluster } from './cluster.class';

describe('Cluster.fromJSON', () => {
  it('keeps legacy containers without a role undefined', () => {
    const cluster = Cluster.fromJSON({
      nodes: [{ id: 'c1', name: 'legacy', kind: 'container', assetId: '', containerPort: 80 }]
    });

    expect((cluster.nodes[0] as any).role).toBeUndefined();
  });

  it('preserves explicit container roles when provided', () => {
    const cluster = Cluster.fromJSON({
      nodes: [{ id: 'c1', name: 'init', kind: 'container', assetId: '', containerPort: 80, role: 'SIDECAR' }]
    });

    expect((cluster.nodes[0] as any).role).toBe('SIDECAR');
  });
});
