import { Node } from './node.class';
import { Link } from './link.class';
import { ClusterStatusEnum } from '../cluster/enum/cluster.-status-enum';

export class Cluster {
    constructor(
        public id: string | null = null,
        public name: string = '',
        public nodes: Node[] = [],
        public links: Link[] = [],
        public diagram: string = '',
        public nameSpace: string = 'default',
        public status: ClusterStatusEnum = ClusterStatusEnum.CREATED
    ) {}

    static fromJSON(apiResponse: any): Cluster {
     if (!apiResponse) return new Cluster(); 
     
     return new Cluster(
         apiResponse.id || null,
         apiResponse.name || '',
         Array.isArray(apiResponse.nodes) ? apiResponse.nodes : [],
         Array.isArray(apiResponse.links) ? apiResponse.links : [],
         apiResponse.diagram || '',
         apiResponse.nameSpace || 'default',
         ClusterStatusEnum[apiResponse.status as keyof typeof ClusterStatusEnum] || ClusterStatusEnum.CREATED
     );
 }

}