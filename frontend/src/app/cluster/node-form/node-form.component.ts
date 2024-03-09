import { Node } from './../../model/node.class';
import { Deployment } from './../../model/deployment.class';
import { ConfigMap } from './../../model/config-map';
import { Store, select } from '@ngrx/store';
import { Component, AfterViewInit, OnDestroy } from '@angular/core';
import { Observable, switchMap, map, of, filter, tap, Subject, Subscription } from 'rxjs';
import { Asset } from '../../model/asset.class';
import { DataService } from 'src/app/services/data.service';
import { DiagramService } from 'src/app/services/diagram.service';
import { FormBuilder, FormGroup } from '@angular/forms';
import { getNodeById } from 'src/app/store/selectors/selectors';
import { Pod } from 'src/app/model/pod.class';

@Component({
  selector: 'app-node-form',
  templateUrl: './node-form.component.html',
  styleUrls: ['./node-form.component.scss']
})
export class NodeFormComponent implements OnDestroy {

  selectedNodeType!: string;
  node!: Node;
  subscription: Subscription = new Subscription();

  constructor(
    private diagramService: DiagramService,
    private store: Store

  ) { }

  ngOnInit(): void {

    this.subscription.add(
      this.diagramService.selectedNode$.pipe(
        filter((node: go.Node) => node !== null && node !== undefined),
        tap((node: go.Node) => this.selectedNodeType = node?.data?.type || null),
        switchMap((node: go.Node) => {
          const nodeId = node?.data?.key;
          if (nodeId) {
            return this.store.select(getNodeById(nodeId));
          } else {
            return of(null);
          }
        }),
        filter((node): node is Node => node !== null && node !== undefined)
      ).subscribe((node: Node) => {
        this.node = node;
      }));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

}
