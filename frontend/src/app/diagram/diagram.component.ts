import { ClusterState } from './../store/states/state';
import { DiagramService } from './../services/diagram.service';
import { IconService } from './../services/icon.service';
import { AfterViewInit, Component, ElementRef, HostListener, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { Store, select } from '@ngrx/store';
import { v4 as uuidv4 } from 'uuid';
import { BehaviorSubject, Subscription, debounceTime, distinctUntilChanged, filter, startWith, take, tap } from 'rxjs';
import * as actions from '../store/actions/actions';
import { getCurrentCluster, selectClusterDiagram } from '../store/selectors/selectors';
import { Cluster } from '../model/cluster.class';
import { DragDropData, DropEvent } from '../directives/drag-drop.directive';
import { ConnectionEvent } from '../directives/node-connector.directive';

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
  selectedLink: DiagramLink | null = null;
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
  paletteItems: DragDropData[] = [];

  constructor(
    private iconService: IconService,
    private store: Store,
    private diagramService: DiagramService
  ) { }



  ngOnInit(): void {
    this.initializePaletteItems();
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

    // Render existing links
    this.renderLinks();
  }

  onCanvasDrop(event: DropEvent) {
    this.createNode(event.data.type, event.data.name, event.data.icon, event.x, event.y);
  }

  onConnectionStart(nodeId: string) {
    console.log('Connection started from node:', nodeId);
  }

  onConnectionEnd(event: ConnectionEvent) {
    this.createLink(event.fromNodeId, event.toNodeId);
  }

  onConnectionCancel() {
    console.log('Connection cancelled');
  }

  private initializePaletteItems() {
    this.paletteItems = this.createNodes();
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

  private createNodes(): DragDropData[] {
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

  onNodeLabelEdit(node: DiagramNode, event: any) {
    node.name = event.target.textContent || node.name;
    this.updateDiagramData();
  }

  onNodeMouseDown(event: MouseEvent, node: DiagramNode) {
    // Prevent default to avoid text selection
    event.preventDefault();

    let isDragging = false;
    const startX = event.clientX;
    const startY = event.clientY;
    const startNodeX = node.x;
    const startNodeY = node.y;

    const onMouseMove = (moveEvent: MouseEvent) => {
      if (!isDragging) {
        // Start dragging if mouse moved enough
        const deltaX = Math.abs(moveEvent.clientX - startX);
        const deltaY = Math.abs(moveEvent.clientY - startY);
        if (deltaX > 5 || deltaY > 5) {
          isDragging = true;
        }
      }

      if (isDragging) {
        const deltaX = moveEvent.clientX - startX;
        const deltaY = moveEvent.clientY - startY;

        node.x = startNodeX + deltaX;
        node.y = startNodeY + deltaY;

        this.updateLinks();
      }
    };

    const onMouseUp = () => {
      if (isDragging) {
        this.updateDiagramData();
      }

      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    };

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
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
    line.setAttribute('stroke', this.selectedLink?.id === link.id ? '#ff4444' : 'lightblue');
    line.setAttribute('stroke-width', this.selectedLink?.id === link.id ? '4' : '3');
    line.setAttribute('marker-end', 'url(#arrowhead)');
    line.style.cursor = 'pointer';
    line.style.pointerEvents = 'stroke';

    // Add click event listener for link selection
    line.addEventListener('click', (event) => {
      event.stopPropagation();
      this.selectLink(link);
    });

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

  selectNode(node: DiagramNode) {
    this.selectedNode = node;
    this.selectedLink = null; // Clear link selection when selecting a node
    this.updateLinkStyles();
  }

  selectLink(link: DiagramLink) {
    this.selectedLink = link;
    this.selectedNode = null; // Clear node selection when selecting a link
    this.updateLinkStyles();
  }

  updateLinkStyles() {
    this.links.forEach(link => {
      if (link.element) {
        const isSelected = this.selectedLink?.id === link.id;
        link.element.setAttribute('stroke', isSelected ? '#ff4444' : 'lightblue');
        link.element.setAttribute('stroke-width', isSelected ? '4' : '3');
      }
    });
  }

  @HostListener('document:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent) {
    if (event.key === 'Delete' || event.key === 'Backspace') {
      if (this.selectedLink) {
        this.deleteSelectedLink();
        event.preventDefault();
      } else if (this.selectedNode) {
        this.deleteSelectedNode();
        event.preventDefault();
      }
    }
  }

  private deleteSelectedLink() {
    if (!this.selectedLink) return;

    // Remove the SVG element
    if (this.selectedLink.element) {
      this.selectedLink.element.remove();
    }

    // Remove from links array
    this.links = this.links.filter(link => link.id !== this.selectedLink!.id);
    this.selectedLink = null;
    this.updateDiagramData();
  }

  private deleteSelectedNode() {
    if (!this.selectedNode) return;

    // Remove all links connected to this node
    const connectedLinks = this.links.filter(link =>
      link.from === this.selectedNode!.id || link.to === this.selectedNode!.id
    );

    connectedLinks.forEach(link => {
      if (link.element) {
        link.element.remove();
      }
    });

    // Remove links from array
    this.links = this.links.filter(link =>
      link.from !== this.selectedNode!.id && link.to !== this.selectedNode!.id
    );

    // Remove node from array
    this.nodes = this.nodes.filter(node => node.id !== this.selectedNode!.id);
    this.selectedNode = null;
    this.updateDiagramData();
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

  private createLink(fromNodeId: string, toNodeId: string) {
    // Check if link already exists
    const existingLink = this.links.find(link =>
      (link.from === fromNodeId && link.to === toNodeId) ||
      (link.from === toNodeId && link.to === fromNodeId)
    );

    if (existingLink) {
      console.log('Link already exists between these nodes');
      return;
    }

    const link: DiagramLink = {
      id: uuidv4(),
      from: fromNodeId,
      to: toNodeId
    };

    this.links.push(link);
    this.renderLink(link);
    this.updateDiagramData();
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
