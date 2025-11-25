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
