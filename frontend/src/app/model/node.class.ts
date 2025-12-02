export class Node {
    id!: string;
    name!: string;
    kind!: string;
    isAffected: boolean;
    workloadType?: string;

    constructor(id: string, name: string, kind: string, isAffected: boolean = false) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.isAffected = isAffected;
    }
}
