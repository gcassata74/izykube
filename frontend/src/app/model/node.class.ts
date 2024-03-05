export class Node {
    id!: string;
    name!: string;
    kind!: string;


   constructor(id: string, name: string, kind: string) {
        this.id = id;
        this.name = name;
        this.kind = kind;
    }

  }
