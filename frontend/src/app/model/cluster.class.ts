import { Node } from './node.class';
import { Link } from './link.class';
import { ClusterStatusEnum } from '../cluster/enum/cluster.-status-enum';

export type ClusterExportMode = 'FLAT_YAML' | 'HELM_CHART';

export class Cluster {
    constructor(
        public id: string | null = null,
        public name: string = '',
        public nodes: Node[] = [],
        public links: Link[] = [],
        public diagram: string = '',
        public nameSpace: string = 'default',
        public status: ClusterStatusEnum = ClusterStatusEnum.CREATED,
        public exportMode: ClusterExportMode = 'FLAT_YAML'
    ) {}

    static fromJSON(apiResponse: any): Cluster {
     if (!apiResponse) return new Cluster(); 
     
     const filteredNodes = Array.isArray(apiResponse.nodes)
         ? apiResponse.nodes.filter((node: any) => (node?.kind ?? node?.type)?.toLowerCase() !== 'pod')
         : [];

     const nodeIds = new Set(filteredNodes.map((node: any) => node?.id));
     const filteredLinks = Array.isArray(apiResponse.links)
         ? apiResponse.links.filter((link: any) => nodeIds.has(link?.source) && nodeIds.has(link?.target))
         : [];

     return new Cluster(
         apiResponse.id || null,
         apiResponse.name || '',
         filteredNodes,
         filteredLinks,
         apiResponse.diagram || '',
         apiResponse.nameSpace || 'default',
         ClusterStatusEnum[apiResponse.status as keyof typeof ClusterStatusEnum] || ClusterStatusEnum.CREATED,
         apiResponse.exportMode === 'HELM_CHART' ? 'HELM_CHART' : 'FLAT_YAML'
     );
 }

}
