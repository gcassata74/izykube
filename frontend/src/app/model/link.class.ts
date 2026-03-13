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

import { v4 as uuidv4 } from 'uuid';
import { ContainerRole, toContainerRole } from './container.class';

export type LinkType = 'Expose' | 'Use' | 'Container' | 'serviceAccountBinding' | 'appliesTo';

export class Link {
    id: string;
    source: string;
    target: string;
    type: LinkType;
    note?: string;
    containerRole?: ContainerRole;

    constructor(init: { source: string; target: string; type?: LinkType; id?: string; note?: string; containerRole?: ContainerRole }) {
        this.id = init.id || uuidv4();
        this.source = init.source;
        this.target = init.target;
        this.type = init.type ?? 'Expose';
        this.note = init.note;
        this.containerRole = toContainerRole(init.containerRole);
    }

    static fromJSON(data: any): Link | null {
        if (!data) {
            return null;
        }

        const source = data.source ?? data.from ?? data.src ?? data.sourceId;
        const target = data.target ?? data.to ?? data.dst ?? data.targetId;

        if (!source || !target) {
            return null;
        }

        const typeRaw = String(data.type ?? '').trim();
        const lowerType = typeRaw.toLowerCase();
        const type: LinkType = typeRaw === 'Use'
            ? 'Use'
            : typeRaw === 'Container'
                ? 'Container'
                : lowerType === 'serviceaccountbinding'
                    ? 'serviceAccountBinding'
                    : lowerType === 'appliesto'
                        ? 'appliesTo'
                            : 'Expose';
        const note = typeof data.note === 'string' ? data.note : undefined;
        const id = typeof data.id === 'string' && data.id.trim() ? data.id : uuidv4();
        const containerRole = toContainerRole(data.containerRole);

        return new Link({
            id,
            source: String(source),
            target: String(target),
            type,
            note,
            containerRole: containerRole ?? undefined
        });
    }
}
