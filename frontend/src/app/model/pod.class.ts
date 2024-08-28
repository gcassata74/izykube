import { Node } from "./node.class";


export type RestartPolicy = 'Always' | 'OnFailure' | 'Never';
export type DNSPolicy = 'ClusterFirst' | 'ClusterFirstWithHostNet' | 'Default' | 'None';
export type PreemptionPolicy = 'PreemptLowerPriority' | 'Never';


export class Pod extends Node{

    restartPolicy: RestartPolicy;
    serviceAccountName?: string;
    nodeSelector?: { [key: string]: string };
    hostNetwork?: boolean;
    dnsPolicy?: DNSPolicy;
    schedulerName?: string;
    priority?: number;
    preemptionPolicy?: PreemptionPolicy;

    constructor(
        id: string,
        name: string,
        restartPolicy: RestartPolicy = 'Always'
    ) {
        super(id, name, 'pod');
        this.restartPolicy = restartPolicy;
    }

}