import { Injectable } from '@angular/core';
import { Pod, RestartPolicy } from '../model/pod.class';
import { Container } from '../model/container.class';
import { Node } from '../model/node.class';
import { Service } from '../model/service.class';
import { Deployment } from '../model/deployment.class';
import { ConfigMap } from '../model/config-map.class';
import { Ingress } from '../model/ingress.class';
import { Volume, VolumeConfig } from '../model/volume.class';
import { Job } from '../model/job.class';

@Injectable({
  providedIn: 'root'
})
export class NodeFactoryService {

  constructor() { }

  createNode(type: string, id: string, name: string): Node {
    switch (type.toLowerCase()) {
      case 'pod':
        return new Pod(
          id,
          name,
          'Always' as RestartPolicy
        );
      case 'container':
        return new Container(
          id,
          name,
          '',  // assetId (empty by default)
          80   // default containerPort
        );
      case 'service':
        return new Service(
          id,
          name,
          'ClusterIP',
          80
        );
        case 'deployment':
          return new Deployment(
            id,
            name,
            1,  // default replicas
            'RollingUpdate'  // default strategy type
          );
      case 'configmap':
        return new ConfigMap(
          id,
          name,
          ''
        );
        case 'job':
          return new Job(
            id,
            name,
            ''
          );  
      case 'volume':
        return new Volume(
          id,
          name,
          { type: 'emptyDir' } as VolumeConfig  // default to emptyDir
        );
      case 'ingress':
        return new Ingress(
          id,
          name,
          'example.com',  // default host
          '/',            // default path
          80              // default servicePort
        );
      default:
        throw new Error(`Unhandled node type: ${type}`);
    }
  }
}