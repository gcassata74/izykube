import { ClusterStatusEnum } from "../cluster/enum/cluster.-status-enum";
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
     status!: ClusterStatusEnum


     static fromJSON(apiResponse: any): Cluster {
          const cluster = new Cluster();
          cluster.id = apiResponse.id || null;
          cluster.name = apiResponse.name;
          cluster.nodes = apiResponse.nodes;
          cluster.links = apiResponse.links;
          cluster.nameSpace = apiResponse.nameSpace;
          cluster.diagram = apiResponse.diagram;
          cluster.status = ClusterStatusEnum[apiResponse.status as keyof typeof ClusterStatusEnum];
          return cluster;
     }
}
