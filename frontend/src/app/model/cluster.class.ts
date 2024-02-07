import { Base } from "./base.class";
import { ClusterNode } from "./node.class";

export class Cluster extends Base{
    public name!: string;
    public nodes!: ClusterNode[];
    public diagram!: string;
}
