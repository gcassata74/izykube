import { ClusterState } from './../store/states/state';
import { DiagramService } from './../services/diagram.service';
import { IconService } from './../services/icon.service';
import { AfterViewInit, Component, ElementRef, HostListener, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import interact from 'interactjs';
import { Store, select } from '@ngrx/store';
import { v4 as uuidv4 } from 'uuid';
import { BehaviorSubject, Subscription, debounceTime, distinctUntilChanged, filter, startWith, take, tap } from 'rxjs';
import * as actions from '../store/actions/actions';
import { getCurrentCluster, selectClusterDiagram } from '../store/selectors/selectors';
import { Cluster } from '../model/cluster.class';

interface DiagramNode {
  id: string;
  name: string;
  type: string;
  icon: string;
  x: number;
  y: number;
  element?: HTMLElement;
}

interface DiagramLink {
  id: string;
  from: string;
  to: string;
  element?: SVGElement;
}

@Component({
  selector: 'app-diagram',
  templateUrl: './diagram.component.html',
  styleUrls: ['./diagram.component.scss']
})
export class DiagramComponent implements OnInit, OnDestroy {

  @ViewChild('container', { static: true }) container!: ElementRef;
  @ViewChild('diagramCanvas', { static: true }) diagramCanvas!: ElementRef;
  @ViewChild('paletteContainer', { static: true }) paletteContainer!: ElementRef;
  
  nodes: DiagramNode[] = [];
  links: DiagramLink[] = [];
  selectedNode: DiagramNode | null = null;
  isResizing: boolean = false;
  firstColumnWidth: number = 180;
  minWidth: number = 200;
  subscription: Subscription = new Subscription();
  layoutOptions = [
    { label: 'Default', value: 'default' },
    { label: 'Circular', value: 'circular' },
    { label: 'Force-Directed', value: 'force-directed' }
  ];
  selectedLayout: string = 'default';
  private svgElement!: SVGElement;

  constructor(
    private iconService: IconService,
    private store: Store,
    private diagramService: DiagramService
  ) { }



  ngOnInit(): void {
    this.createPalette();
    this.store.pipe(
      select(selectClusterDiagram),
      debounceTime(1000),
      tap(diagramData => {
        if(diagramData !== null && diagramData !== undefined && diagramData !== "") {
          this.loadDiagramData(diagramData);
        }
        this.initializeDiagram();
      }),
      take(1)
    ).subscribe();
  }


  private initializeDiagram() {
    const canvas = this.diagramCanvas.nativeElement;
    
    // Create SVG for links
    this.svgElement = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    this.svgElement.style.position = 'absolute';
    this.svgElement.style.top = '0';
    this.svgElement.style.left = '0';
    this.svgElement.style.width = '100%';
    this.svgElement.style.height = '100%';
    this.svgElement.style.pointerEvents = 'none';
    this.svgElement.style.zIndex = '1';
    canvas.appendChild(this.svgElement);

    // Setup drag and drop from palette
    this.setupPaletteDragDrop();
    
    // Setup canvas drop zone
    this.setupCanvasDropZone();
    
    // Render existing nodes
    this.renderNodes();
    this.renderLinks();
  }

 
  private setupPaletteDragDrop() {
    const paletteItems = this.paletteContainer.nativeElement.querySelectorAll('.palette-item');
    
    paletteItems.forEach((item: HTMLElement) => {
      interact(item)
        .draggable({
          inertia: true,
          modifiers: [
            interact.modifiers.restrictRect({
              restriction: 'parent',
              endOnly: true
            })
          ],
          autoScroll: true,
          listeners: {
            start: (event) => {
              event.target.style.opacity = '0.5';
            },
            move: (event) => {
              const target = event.target;
              const x = (parseFloat(target.getAttribute('data-x')) || 0) + event.dx;
              const y = (parseFloat(target.getAttribute('data-y')) || 0) + event.dy;
              
              target.style.transform = `translate(${x}px, ${y}px)`;
              target.setAttribute('data-x', x.toString());
              target.setAttribute('data-y', y.toString());
            },
            end: (event) => {
              event.target.style.opacity = '1';
              event.target.style.transform = '';
              event.target.removeAttribute('data-x');
              event.target.removeAttribute('data-y');
            }
          }
        });
    });
  }

  private setupCanvasDropZone() {
    const canvas = this.diagramCanvas.nativeElement;
    
    interact(canvas)
      .dropzone({
        accept: '.palette-item',
        overlap: 0.1,
        ondrop: (event) => {
          const droppedElement = event.relatedTarget;
          const nodeType = droppedElement.getAttribute('data-type');
          const nodeName = droppedElement.getAttribute('data-name');
          const nodeIcon = droppedElement.getAttribute('data-icon');
          
          const rect = canvas.getBoundingClientRect();
          const x = event.dragEvent.clientX - rect.left;
          const y = event.dragEvent.clientY - rect.top;
          
          this.createNode(nodeType, nodeName, nodeIcon, x, y);
        }
      });
  }

  private createNode(type: string, baseName: string, icon: string, x: number, y: number) {
    const uniqueName = this.generateUniqueName(baseName);
    const node: DiagramNode = {
      id: uuidv4(),
      name: uniqueName,
      type: type,
      icon: icon,
      x: x,
      y: y
    };
    
    this.nodes.push(node);
    this.renderNode(node);
    this.updateDiagramData();
  }

  private generateUniqueName(baseName: string): string {
    const similarNodes = this.nodes.filter(node => 
      node.name && node.name.startsWith(baseName)
    );

    let newName = baseName;
    let maxSuffixCharCode = 'a'.charCodeAt(0) - 1;

    similarNodes.forEach(node => {
      const result = node.name.match(/^(.*?)-([a-zA-Z])$/);
      if (result && result[2]) {
        const charCode = result[2].charCodeAt(0);
        if (charCode > maxSuffixCharCode) {
          maxSuffixCharCode = charCode;
        }
      }
    });

    if (similarNodes.length > 0) {
      const newSuffixChar = String.fromCharCode(maxSuffixCharCode + 1);
      newName = `${baseName}-${newSuffixChar}`;
    }

    return newName;
  }

  private createPalette() {
    const paletteContainer = this.paletteContainer.nativeElement;
    const nodeDataArray = this.createNodes();

    nodeDataArray.forEach(nodeData => {
      const paletteItem = document.createElement('div');
      paletteItem.className = 'palette-item';
      paletteItem.setAttribute('data-type', nodeData.type);
      paletteItem.setAttribute('data-name', nodeData.name);
      paletteItem.setAttribute('data-icon', nodeData.icon);
      
      const icon = document.createElement('img');
      icon.src = nodeData.icon;
      icon.alt = nodeData.name;
      icon.style.width = '40px';
      icon.style.height = '40px';
      
      const label = document.createElement('div');
      label.textContent = nodeData.name;
      label.className = 'palette-label';
      
      paletteItem.appendChild(icon);
      paletteItem.appendChild(label);
      paletteContainer.appendChild(paletteItem);
    });
  }

  private createNodes() {
    return [
      { name: 'ingress', type: 'ingress', icon: this.iconService.getIconPath('ingress') },
      { name: 'container', type: 'container', icon: this.iconService.getIconPath('container') },
      { name: 'pod', type: 'pod', icon: this.iconService.getIconPath('pod') },
      { name: 'deployment', type: 'deployment', icon: this.iconService.getIconPath('deployment') },
      { name: 'service', type: 'service', icon: this.iconService.getIconPath('service') },
      { name: 'configmap', type: 'configmap', icon: this.iconService.getIconPath('configmap') },
      { name: 'volume', type: 'volume', icon: this.iconService.getIconPath('volume') },
      { name: 'job', type: 'job', icon: this.iconService.getIconPath('job') } 
    ];
  }

  private renderNodes() {
    this.nodes.forEach(node => this.renderNode(node));
  }

  private renderNode(node: DiagramNode) {
    const canvas = this.diagramCanvas.nativeElement;
    
    const nodeElement = document.createElement('div');
    nodeElement.className = 'diagram-node';
    nodeElement.style.position = 'absolute';
    nodeElement.style.left = `${node.x}px`;
    nodeElement.style.top = `${node.y}px`;
    nodeElement.style.width = '80px';
    nodeElement.style.height = '80px';
    nodeElement.style.border = '3px solid #ccc';
    nodeElement.style.borderRadius = '10px';
    nodeElement.style.backgroundColor = 'white';
    nodeElement.style.display = 'flex';
    nodeElement.style.flexDirection = 'column';
    nodeElement.style.alignItems = 'center';
    nodeElement.style.justifyContent = 'center';
    nodeElement.style.cursor = 'move';
    nodeElement.style.zIndex = '2';
    
    const icon = document.createElement('img');
    icon.src = node.icon;
    icon.alt = node.name;
    icon.style.width = '40px';
    icon.style.height = '40px';
    
    const label = document.createElement('div');
    label.textContent = node.name;
    label.style.fontSize = '10px';
    label.style.textAlign = 'center';
    label.style.marginTop = '2px';
    label.contentEditable = 'true';
    
    nodeElement.appendChild(icon);
    nodeElement.appendChild(label);
    canvas.appendChild(nodeElement);
    
    node.element = nodeElement;
    
    // Make node draggable
    interact(nodeElement)
      .draggable({
        listeners: {
          move: (event) => {
            node.x += event.dx;
            node.y += event.dy;
            
            nodeElement.style.left = `${node.x}px`;
            nodeElement.style.top = `${node.y}px`;
            
            this.updateLinks();
            this.updateDiagramData();
          }
        }
      })
      .on('tap', () => {
        this.selectNode(node);
      });
    
    // Handle label editing
    label.addEventListener('blur', () => {
      node.name = label.textContent || node.name;
      this.updateDiagramData();
    });
  }

  private renderLinks() {
    this.links.forEach(link => this.renderLink(link));
  }

  private renderLink(link: DiagramLink) {
    const fromNode = this.nodes.find(n => n.id === link.from);
    const toNode = this.nodes.find(n => n.id === link.to);
    
    if (!fromNode || !toNode) return;
    
    const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    line.setAttribute('x1', (fromNode.x + 40).toString());
    line.setAttribute('y1', (fromNode.y + 40).toString());
    line.setAttribute('x2', (toNode.x + 40).toString());
    line.setAttribute('y2', (toNode.y + 40).toString());
    line.setAttribute('stroke', 'lightblue');
    line.setAttribute('stroke-width', '3');
    line.setAttribute('marker-end', 'url(#arrowhead)');
    
    this.svgElement.appendChild(line);
    link.element = line;
  }

  private updateLinks() {
    this.links.forEach(link => {
      const fromNode = this.nodes.find(n => n.id === link.from);
      const toNode = this.nodes.find(n => n.id === link.to);
      
      if (fromNode && toNode && link.element) {
        link.element.setAttribute('x1', (fromNode.x + 40).toString());
        link.element.setAttribute('y1', (fromNode.y + 40).toString());
        link.element.setAttribute('x2', (toNode.x + 40).toString());
        link.element.setAttribute('y2', (toNode.y + 40).toString());
      }
    });
  }

  private selectNode(node: DiagramNode) {
    // Clear previous selection
    if (this.selectedNode && this.selectedNode.element) {
      this.selectedNode.element.style.borderColor = '#ccc';
    }
    
    this.selectedNode = node;
    if (node.element) {
      node.element.style.borderColor = '#007bff';
    }
  }

  private loadDiagramData(diagramData: string) {
    try {
      const data = JSON.parse(diagramData);
      this.nodes = data.nodes || [];
      this.links = data.links || [];
    } catch (error) {
      console.error('Error loading diagram data:', error);
      this.nodes = [];
      this.links = [];
    }
  }

  private updateDiagramData() {
    const diagramData = JSON.stringify({
      nodes: this.nodes.map(n => ({ id: n.id, name: n.name, type: n.type, icon: n.icon, x: n.x, y: n.y })),
      links: this.links.map(l => ({ id: l.id, from: l.from, to: l.to }))
    });
    
    this.store.dispatch(actions.updateDiagram({ diagramData }));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
