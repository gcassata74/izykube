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
