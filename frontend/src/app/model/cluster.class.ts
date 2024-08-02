import { ConfigMap } from "./config-map.class";
import { Deployment } from "./deployment.class";
import { Link } from "./link.class";
import { Node } from "./node.class";
import { Pod } from "./pod.class";

export class Cluster {
     id!: string | null;
     name!: string;
     nodes!: Node[];
     links!: Link[];
     nameSpace!: string;
     diagram!: string;
     isDeployed!: boolean;
     hasTemplate!: boolean;
}
