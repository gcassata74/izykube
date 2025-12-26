import { v4 as uuidv4 } from 'uuid';
import { ContainerRole, toContainerRole } from './container.class';

export type LinkType = 'Expose' | 'Use' | 'Container' | 'serviceAccountBinding';

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
        const type: LinkType = typeRaw === 'Use'
            ? 'Use'
            : typeRaw === 'Container'
                ? 'Container'
                : typeRaw.toLowerCase() === 'serviceaccountbinding'
                    ? 'serviceAccountBinding'
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
