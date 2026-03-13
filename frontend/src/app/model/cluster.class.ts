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

import { Node } from './node.class';
import { Link } from './link.class';
import { ClusterStatusEnum } from '../cluster/enum/cluster.-status-enum';
import { toContainerRole } from './container.class';

export type ClusterExportMode = 'FLAT_YAML' | 'HELM_CHART';

export class Cluster {
    constructor(
        public id: string | null = null,
        public name: string = '',
        public nodes: Node[] = [],
        public links: Link[] = [],
        public diagram: string = '',
        public nameSpace: string = 'default',
        public status: ClusterStatusEnum = ClusterStatusEnum.CREATED,
        public exportMode: ClusterExportMode = 'FLAT_YAML'
    ) {}

    static fromJSON(apiResponse: any): Cluster {
     if (!apiResponse) return new Cluster(); 
     
     const filteredNodes = Array.isArray(apiResponse.nodes)
         ? apiResponse.nodes.filter((node: any) => (node?.kind ?? node?.type)?.toLowerCase() !== 'pod')
         : [];

     const normalizedNodes = filteredNodes.map((node: any) => Cluster.normalizeContainerNode(node));

     const nodeIds = new Set(normalizedNodes.map((node: any) => node?.id));
     const filteredLinks = Array.isArray(apiResponse.links)
         ? apiResponse.links
             .map((link: any) => Link.fromJSON(link))
             .filter((link: Link | null): link is Link => !!link)
             .filter((link: Link) => nodeIds.has(link.source) && nodeIds.has(link.target))
         : [];

     return new Cluster(
         apiResponse.id || null,
         apiResponse.name || '',
         normalizedNodes,
         filteredLinks,
         apiResponse.diagram || '',
         apiResponse.nameSpace || 'default',
         ClusterStatusEnum[apiResponse.status as keyof typeof ClusterStatusEnum] || ClusterStatusEnum.CREATED,
         apiResponse.exportMode === 'HELM_CHART' ? 'HELM_CHART' : 'FLAT_YAML'
     );
 }

    private static normalizeContainerNode(node: any): any {
        const kind = (node?.kind ?? node?.type ?? '').toLowerCase();
        if (kind !== 'container') {
            return node;
        }

        const normalizedRole = toContainerRole(node?.role);
        if (!normalizedRole) {
            const { role, ...rest } = node;
            return rest;
        }

        return {
            ...node,
            role: normalizedRole
        };
    }

}
