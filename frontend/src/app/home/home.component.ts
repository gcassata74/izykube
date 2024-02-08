import { DiagramComponent } from './../diagram/diagram.component';
import { ToolbarService } from './../services/toolbar.service';
import {Component, ElementRef, AfterViewInit, OnInit, HostListener, ViewChild} from '@angular/core';
import{Button} from '../model/button.class';
import { Store, select } from '@ngrx/store';
import { getCurrentAction, getClusterData } from '../store/selectors/selectors';
import { filter, first, tap } from 'rxjs';
import * as actions from '../store/actions/actions';
import * as go from 'gojs';
import { Cluster } from '../model/cluster.class';

import { DiagramService } from '../services/diagram.service';
import { ConfigMap, Container, Deployment, Ingress, Pod, Service } from '../model/node.class';
import { addNode, removeNode } from '../store/actions/cluster.actions';


@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  ngOnInit(): void {
    throw new Error('Method not implemented.');
  }

 
}
