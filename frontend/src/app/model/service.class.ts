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

export class Service extends Node {
    type: ServiceType;
    port: number;
    nodePort?: number;
    exposeService: boolean;
    frontendUrl?: string;
    forwardEnabled?: boolean;
    forwardPort?: number;
    forwardTargetPort?: number;
    forwardActive?: boolean;

    constructor(
        id: string,
        name: string,
        type: ServiceType,
        port: number,
        exposeService: boolean = false,
        frontendUrl?: string,
        nodePort?: number,
        forwardEnabled: boolean = false,
        forwardPort?: number,
        forwardTargetPort?: number,
        forwardActive: boolean = false
    ) {
        super(id, name, 'service');
        this.type = type;
        this.port = port;
        this.nodePort = nodePort;
        this.exposeService = exposeService;
        this.frontendUrl = frontendUrl;
        this.forwardEnabled = forwardEnabled;
        this.forwardPort = forwardPort;
        this.forwardTargetPort = forwardTargetPort;
        this.forwardActive = forwardActive;
    }

    // Existing methods...

    setExposeService(expose: boolean) {
        this.exposeService = expose;
        if (!expose) {
            this.frontendUrl = undefined;
        }
    }

    setFrontendUrl(url: string) {
        if (this.exposeService) {
            this.frontendUrl = url;
        }
    }
}

export type ServiceType = 'ClusterIP' | 'NodePort' | 'LoadBalancer' | 'ExternalName';
