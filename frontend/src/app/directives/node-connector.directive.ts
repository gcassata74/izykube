import { Directive, ElementRef, EventEmitter, HostListener, Input, Output, OnDestroy } from '@angular/core';

export interface ConnectionEvent {
  fromNodeId: string;
  toNodeId: string;
}

@Directive({
  selector: '[appNodeConnector]'
})
export class NodeConnectorDirective implements OnDestroy {
  @Input() nodeId: string = '';
  @Output() onConnectionStart = new EventEmitter<string>();
  @Output() onConnectionEnd = new EventEmitter<ConnectionEvent>();
  @Output() onConnectionCancel = new EventEmitter<void>();

  private static isConnecting = false;
  private static sourceNodeId: string | null = null;
  private static connectionLine: SVGLineElement | null = null;
  private static svgElement: SVGElement | null = null;

  constructor(private el: ElementRef) {}

  @HostListener('click', ['$event'])
  onClick(event: MouseEvent) {
    event.stopPropagation();
    
    if (!NodeConnectorDirective.isConnecting) {
      // Start connection
      this.startConnection();
    } else {
      // End connection
      this.endConnection();
    }
  }

  @HostListener('mouseenter', ['$event'])
  onMouseEnter(event: MouseEvent) {
    if (NodeConnectorDirective.isConnecting && NodeConnectorDirective.sourceNodeId !== this.nodeId) {
      this.el.nativeElement.style.borderColor = '#28a745';
      this.el.nativeElement.style.borderWidth = '3px';
    }
  }

  @HostListener('mouseleave', ['$event'])
  onMouseLeave(event: MouseEvent) {
    if (NodeConnectorDirective.isConnecting && NodeConnectorDirective.sourceNodeId !== this.nodeId) {
      this.el.nativeElement.style.borderColor = '#ccc';
      this.el.nativeElement.style.borderWidth = '3px';
    }
  }

  @HostListener('document:mousemove', ['$event'])
  onDocumentMouseMove(event: MouseEvent) {
    if (NodeConnectorDirective.isConnecting && 
        NodeConnectorDirective.sourceNodeId === this.nodeId && 
        NodeConnectorDirective.connectionLine) {
      
      const sourceRect = this.el.nativeElement.getBoundingClientRect();
      const svgRect = NodeConnectorDirective.svgElement?.getBoundingClientRect();
      
      if (svgRect) {
        const sourceX = sourceRect.left + sourceRect.width / 2 - svgRect.left;
        const sourceY = sourceRect.top + sourceRect.height / 2 - svgRect.top;
        const targetX = event.clientX - svgRect.left;
        const targetY = event.clientY - svgRect.top;
        
        NodeConnectorDirective.connectionLine.setAttribute('x1', sourceX.toString());
        NodeConnectorDirective.connectionLine.setAttribute('y1', sourceY.toString());
        NodeConnectorDirective.connectionLine.setAttribute('x2', targetX.toString());
        NodeConnectorDirective.connectionLine.setAttribute('y2', targetY.toString());
      }
    }
  }

  @HostListener('document:keydown', ['$event'])
  onDocumentKeyDown(event: KeyboardEvent) {
    if (event.key === 'Escape' && NodeConnectorDirective.isConnecting) {
      this.cancelConnection();
    }
  }

  private startConnection() {
    NodeConnectorDirective.isConnecting = true;
    NodeConnectorDirective.sourceNodeId = this.nodeId;
    
    // Find SVG element in the parent container
    const canvas = this.findCanvasElement();
    if (canvas) {
      NodeConnectorDirective.svgElement = canvas.querySelector('svg');
      
      if (NodeConnectorDirective.svgElement) {
        // Create temporary connection line
        NodeConnectorDirective.connectionLine = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        NodeConnectorDirective.connectionLine.setAttribute('stroke', '#007bff');
        NodeConnectorDirective.connectionLine.setAttribute('stroke-width', '2');
        NodeConnectorDirective.connectionLine.setAttribute('stroke-dasharray', '5,5');
        NodeConnectorDirective.connectionLine.style.pointerEvents = 'none';
        
        NodeConnectorDirective.svgElement.appendChild(NodeConnectorDirective.connectionLine);
      }
    }
    
    // Visual feedback for source node
    this.el.nativeElement.style.borderColor = '#007bff';
    this.el.nativeElement.style.borderWidth = '3px';
    
    this.onConnectionStart.emit(this.nodeId);
  }

  private endConnection() {
    if (NodeConnectorDirective.sourceNodeId && 
        NodeConnectorDirective.sourceNodeId !== this.nodeId) {
      
      const connectionEvent: ConnectionEvent = {
        fromNodeId: NodeConnectorDirective.sourceNodeId,
        toNodeId: this.nodeId
      };
      
      this.onConnectionEnd.emit(connectionEvent);
    }
    
    this.resetConnection();
  }

  private cancelConnection() {
    this.onConnectionCancel.emit();
    this.resetConnection();
  }

  private resetConnection() {
    NodeConnectorDirective.isConnecting = false;
    NodeConnectorDirective.sourceNodeId = null;
    
    // Remove temporary line
    if (NodeConnectorDirective.connectionLine && NodeConnectorDirective.svgElement) {
      NodeConnectorDirective.svgElement.removeChild(NodeConnectorDirective.connectionLine);
      NodeConnectorDirective.connectionLine = null;
    }
    
    // Reset all node styles
    const allNodes = document.querySelectorAll('.diagram-node');
    allNodes.forEach(node => {
      (node as HTMLElement).style.borderColor = '#ccc';
      (node as HTMLElement).style.borderWidth = '3px';
    });
    
    NodeConnectorDirective.svgElement = null;
  }

  private findCanvasElement(): Element | null {
    let element = this.el.nativeElement.parentElement;
    while (element) {
      if (element.classList.contains('second-column')) {
        return element;
      }
      element = element.parentElement;
    }
    return null;
  }

  ngOnDestroy() {
    if (NodeConnectorDirective.sourceNodeId === this.nodeId) {
      this.resetConnection();
    }
  }
}
