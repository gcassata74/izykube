import { ConfigMap } from "../model/config-map.interface";
import { Container } from "../model/container.interface";
import { Deployment } from "../model/deployment.interface";
import { Ingress } from "../model/ingress.interface";
import { PodSpec } from "../model/pod.interface";
import { Service } from "../model/service.interface";
import { Volume } from "../model/volume.interface";

export type NodeTemplateFunctions = {
    pod: () => PodSpec,
    container: () => Container,
    ingress: () => Ingress,
    service: () => Service,
    configMap: () => ConfigMap,
    deployment: () => Deployment,
    volume: () => Volume
  };


export const nodeTemplates: NodeTemplateFunctions = {
    pod: () => ({ id: '', name: '', kind: 'pod', containers: [], volumes: [] }),
    container: () => ({ id: '', name: '', kind: 'container', assetId: ''}),
    ingress: () => ({ id: '', name: '', kind: 'ingress', rules: [] }),
    service: () => ({ id: '', name: '', kind: 'service', type: 'ClusterIP', ports: [{ port: 80, targetPort: 80, protocol: 'TCP' }], selector: {} }),
    configMap: () => ({ id: '', name: '', kind: 'configMap', data: {} }),
    deployment: () => ({
        id: '',
        name: '',
        kind: 'deployment',
        replicas: 1,
        selector: { matchLabels: {} },
        template: {
            metadata: { labels: {} },
            spec: {
                containers: [],
                id: '',
                name: '',
                kind: 'pod'
            }
        },
        containerDtos: []
    }),
    volume: () => ({ id: '', name: '', kind: 'volume', persistentVolumeClaim: { claimName: '' } })
  };
