import { Injectable } from '@angular/core';
import { Container } from '../model/container.class';
import { Node } from '../model/node.class';
import { Service } from '../model/service.class';
import { Deployment } from '../model/deployment.class';
import { ConfigBundleNode } from '../model/config-bundle-node.class';
import { Ingress } from '../model/ingress.class';
import { Volume, VolumeConfig } from '../model/volume.class';
import { Job } from '../model/job.class';
import { Istio } from '../model/istio.class';

@Injectable({
  providedIn: 'root'
})
export class NodeFactoryService {

  constructor() { }

  createNode(type: string, id: string, name: string): Node {
    switch (type.toLowerCase()) {
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
          1,
          'RollingUpdate',
          '',
          80,
          'DEPLOYMENT'
        );
      case 'configmap':
        return new ConfigBundleNode(id, name, {
          namespace: 'default',
          annotations: {},
          entries: []
        }, 'configmap');
      case 'secret':
        return new ConfigBundleNode(id, name, {
          namespace: 'default',
          annotations: {},
          entries: []
        }, 'secret');
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
          '',
          80,             // default servicePort
          '',
          {}
        );
      case 'istio':
        return new Istio(
          id,
          name,
          'example.com',
          '/',
          '',
          80
        );
      default:
        throw new Error(`Unhandled node type: ${type}`);
    }
  }
}
