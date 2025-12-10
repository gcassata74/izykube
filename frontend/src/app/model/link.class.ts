import { v4 as uuidv4 } from 'uuid';

export type LinkType = 'Expose' | 'Use';

export class Link {
    id: string;
    source: string;
    target: string;
    type: LinkType;
    note?: string;

    constructor(init: { source: string; target: string; type?: LinkType; id?: string; note?: string }) {
        this.id = init.id || uuidv4();
        this.source = init.source;
        this.target = init.target;
        this.type = init.type ?? 'Expose';
        this.note = init.note;
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

        const type = data.type === 'Use' ? 'Use' : 'Expose';
        const note = typeof data.note === 'string' ? data.note : undefined;
        const id = typeof data.id === 'string' && data.id.trim() ? data.id : uuidv4();

        return new Link({
            id,
            source: String(source),
            target: String(target),
            type,
            note
        });
    }
}
