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

import { Container, toContainerRole } from './container.class';

describe('Container model', () => {
  it('leaves role undefined when not provided', () => {
    const container = new Container('id', 'web', 'asset', 80);
    expect(container.role).toBeUndefined();
  });

  it('normalizes arbitrary role values via toContainerRole', () => {
    expect(toContainerRole('INIT')).toBe('INIT');
    expect(toContainerRole('SIDECAR')).toBe('SIDECAR');
    expect(toContainerRole('unexpected')).toBeUndefined();
  });
});
