import { ClusterStatusEnum } from '../cluster/enum/cluster.-status-enum';
import { Link } from './link.class';
import { Node } from './node.class';

export interface ClusterVersion {
  id: string;
  clusterId: string;
  clusterName: string;
  namespace: string;
  versionNumber: number;
  diagram: string;
  nodes: Node[];
  links: Link[];
  status: ClusterStatusEnum | string;
  createdAt: string;
}
