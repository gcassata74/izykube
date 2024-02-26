import { Injectable } from '@angular/core';
import { Pod } from '../model/pod.class';
import { Container } from '../model/container';
import { Node } from '../model/node.class';
import { Service } from '../model/service.class';
import { Deployment } from '../model/deployment.class';
import { ConfigMap } from '../model/config-map';

@Injectable({
  providedIn: 'root'
})
export class NodeFactoryService {

  constructor() { }


  createNode(type: string, id: string, name: string): Node {
    switch (type.toLowerCase()) {
      case 'pod':
        return new Pod(id, name, type.toLowerCase());
      case 'container':
        return new Container(id, name,type.toLowerCase());
      case 'service':
        return new Service(id, name,type.toLowerCase());  
      case 'deployment':
        return new Deployment(id, name,type.toLowerCase());    
      case 'configmap':
        return new ConfigMap(id, name,type.toLowerCase());      
      // Handle other types...
      default:
        throw new Error(`Unhandled node type: ${type}`);
    }
  }
}
