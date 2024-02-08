import { Base } from "./base.class";
import { ClusterNode, Link } from "./node.class";

export class Cluster extends Base{
    public name!: string;
    public nodes!: ClusterNode[];
    public links!: Link[];
    public diagram!: string;
}
