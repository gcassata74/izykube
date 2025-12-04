import { Directive, ElementRef, EventEmitter, HostListener, Input, Output, AfterViewInit, OnDestroy } from '@angular/core';

export interface DragDropData {
  type: string;
  name: string;
  icon: string;
  x?: number;
  y?: number;
  displayName?: string;
  baseName?: string;
}

export interface DropEvent {
  data: DragDropData;
  x: number;
  y: number;
}

@Directive({
  selector: '[appDragDrop]'
})
export class DragDropDirective implements AfterViewInit, OnDestroy {
  @Input() dragData: DragDropData | null = null;
  @Input() isDraggable: boolean = false;
  @Input() isDropZone: boolean = false;
  @Output() onDrop = new EventEmitter<DropEvent>();
  @Output() onDragStart = new EventEmitter<DragDropData>();
  @Output() onDragEnd = new EventEmitter<void>();

  private isDragging = false;
  private dragOffset = { x: 0, y: 0 };

  constructor(private el: ElementRef) {}

  @HostListener('mousedown', ['$event'])
  onMouseDown(event: MouseEvent) {
    if (!this.isDraggable || !this.dragData) return;
    
    event.preventDefault();
    this.isDragging = true;
    
    const rect = this.el.nativeElement.getBoundingClientRect();
    this.dragOffset.x = event.clientX - rect.left;
    this.dragOffset.y = event.clientY - rect.top;
    
    this.el.nativeElement.style.opacity = '0.5';
    this.el.nativeElement.style.zIndex = '1000';
    this.onDragStart.emit(this.dragData);
    
    document.addEventListener('mousemove', this.onMouseMove);
    document.addEventListener('mouseup', this.onMouseUp);
  }

  private onMouseMove = (event: MouseEvent) => {
    if (!this.isDragging) return;
    
    const x = event.clientX - this.dragOffset.x;
    const y = event.clientY - this.dragOffset.y;
    
    this.el.nativeElement.style.position = 'fixed';
    this.el.nativeElement.style.left = `${x}px`;
    this.el.nativeElement.style.top = `${y}px`;
    this.el.nativeElement.style.pointerEvents = 'none';
  };

  private onMouseUp = (event: MouseEvent) => {
    if (!this.isDragging) return;
    
    this.isDragging = false;
    this.el.nativeElement.style.opacity = '1';
    this.el.nativeElement.style.zIndex = '';
    this.el.nativeElement.style.position = '';
    this.el.nativeElement.style.left = '';
    this.el.nativeElement.style.top = '';
    this.el.nativeElement.style.pointerEvents = '';
    
    // Find drop zone under cursor
    const elementUnderCursor = document.elementFromPoint(event.clientX, event.clientY);
    const dropZone = this.findDropZone(elementUnderCursor);
    
    if (dropZone && this.dragData) {
      const dropZoneRect = dropZone.getBoundingClientRect();
      const dropEvent: DropEvent = {
        data: this.dragData,
        x: event.clientX - dropZoneRect.left,
        y: event.clientY - dropZoneRect.top
      };
      
      // Emit drop event on the drop zone directive
      const dropDirective = (dropZone as any).__dragDropDirective;
      if (dropDirective) {
        dropDirective.onDrop.emit(dropEvent);
      }
    }
    
    this.onDragEnd.emit();
    
    document.removeEventListener('mousemove', this.onMouseMove);
    document.removeEventListener('mouseup', this.onMouseUp);
  };

  private findDropZone(element: Element | null): Element | null {
    while (element) {
      if ((element as any).__dragDropDirective?.isDropZone) {
        return element;
      }
      element = element.parentElement;
    }
    return null;
  }

  ngAfterViewInit() {
    // Store reference to directive on the element for drop zone detection
    (this.el.nativeElement as any).__dragDropDirective = this;
  }

  ngOnDestroy() {
    document.removeEventListener('mousemove', this.onMouseMove);
    document.removeEventListener('mouseup', this.onMouseUp);
  }
}
