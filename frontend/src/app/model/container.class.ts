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

import { Node } from "./node.class";

export const CONTAINER_ROLE_VALUES = ['INIT', 'SIDECAR'] as const;
export type ContainerRole = typeof CONTAINER_ROLE_VALUES[number];

export function toContainerRole(value: unknown): ContainerRole | undefined {
    return (CONTAINER_ROLE_VALUES as readonly string[]).includes(value as ContainerRole)
        ? value as ContainerRole
        : undefined;
}

export class Container extends Node {
    assetId: string;
    containerPort: number;
    role?: ContainerRole;

    constructor(id: string, name: string, assetId: string, containerPort: number, role?: ContainerRole) {
        super(id, name, 'container');
        this.assetId = assetId;
        this.containerPort = containerPort;
        this.role = toContainerRole(role);
    }
}
