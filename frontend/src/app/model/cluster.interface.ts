import { Base } from "./base.interface";
import { Link } from "./link.interface";

export interface Cluster {
     id: string;
     name: string;
     nodes: Base[]
     links: Link[];
     diagram: string;
     creationDate: Date;
     lastUpdated: Date;
}
