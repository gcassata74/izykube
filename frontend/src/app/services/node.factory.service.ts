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
import { ServiceAccount } from '../model/service-account.class';
import { AccessPolicy } from '../model/access-policy.class';

@Injectable({
  providedIn: 'root'
})
export class NodeFactoryService {

  constructor() { }

  createNode(type: string, id: string, name: string): Node {
    const trimmedName = typeof name === 'string' ? name.trim() : name;
    switch (type.toLowerCase()) {
      case 'container':
        return new Container(
          id,
          trimmedName,
          '',  // assetId (empty by default)
          80   // default containerPort
        );
      case 'service':
        return new Service(
          id,
          trimmedName,
          'ClusterIP',
          80
        );
      case 'deployment':
        return new Deployment(
          id,
          trimmedName,
          1,
          'RollingUpdate',
          '',
          80,
          'DEPLOYMENT'
        );
      case 'configmap':
        return new ConfigBundleNode(id, trimmedName, {
          namespace: 'default',
          annotations: {},
          entries: []
        }, 'configmap');
      case 'configbundle':
        return new ConfigBundleNode(id, trimmedName, {
          namespace: 'default',
          annotations: {},
          entries: []
        }, 'configbundle');
      case 'secret':
        return new ConfigBundleNode(id, trimmedName, {
          namespace: 'default',
          annotations: {},
          entries: []
        }, 'secret');
      case 'job':
          return new Job(
            id,
            trimmedName,
            ''
          );  
      case 'serviceaccount':
        return new ServiceAccount(id, trimmedName, 'default', true, {}, {}, 'NONE');
      case 'accesspolicy':
        return new AccessPolicy(id, trimmedName, 'default', [], 'WORKLOAD_SA_PER_WORKLOAD', null);
      case 'volume':
        return new Volume(
          id,
          trimmedName,
          { type: 'emptyDir' } as VolumeConfig  // default to emptyDir
        );
      case 'ingress':
        return new Ingress(
          id,
          trimmedName,
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
          trimmedName,
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
