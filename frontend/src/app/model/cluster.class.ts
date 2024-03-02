import { Link } from "./link.class";
import { Node } from "./node.class";

export class Cluster {
     id!: string | null;
     name!: string;
     nodes!: Node[]
     links!: Link[];
     diagram!: string;
     creationDate!: Date;
     lastUpdated!: Date;
}
