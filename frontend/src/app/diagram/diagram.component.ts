import { DiagramService } from './../services/diagram.service';
import { IconService } from './../services/icon.service';
import { AfterViewInit, Component, ElementRef, HostListener, NgZone, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Store, select } from '@ngrx/store';
import { v4 as uuidv4 } from 'uuid';
import { Subscription, debounceTime, filter, finalize, take, tap } from 'rxjs';
import * as actions from '../store/actions/actions';
import { getCurrentCluster, getNodeById, selectClusterDiagram } from '../store/selectors/selectors';
import { Cluster, ClusterExportMode } from '../model/cluster.class';
import { DragDropData, DropEvent } from '../directives/drag-drop.directive';
import { AiAssistantService, AiChatMessage, AiImportYamlResponse, AiExportYamlResponse, AiHelmChartExportResponse } from '../services/ai-assistant.service';
import { NotificationService } from '../services/notification.service';
import { Link } from '../model/link.class';
import { ContainerRole, toContainerRole } from '../model/container.class';
import { ClusterStatusEnum } from '../cluster/enum/cluster.-status-enum';
import { PodShellService } from '../services/pod-shell.service';
import { PodSummary } from '../model/kube-summary';
import { OverlayPanel } from 'primeng/overlaypanel';
import { ConfigurationChangeService } from '../services/configuration-change.service';
import { ResourceSyncService } from '../services/resource-sync.service';
import { ConfigBundleMeta } from '../model/config-bundle.model';
import interact from 'interactjs';

  /* Manual verification checklist:
   * - Open a diagram with multiple nodes and links.
   * - Drag a node; links stay attached and move with the node.
   * - Click a link to select it; press Delete and ensure it disappears from canvas and model.
   * - Pan/zoom and confirm links remain aligned; minimap viewport moves during pan.
   * - Drag from a connection point to another to create a link; click a link and delete with Delete key.
   * Note: Node dragging is disabled on connection-point handles (ignoreFrom) so link gestures are not hijacked.
   */

interface DiagramNode {
  id: string;
  name: string;
  type: string;
  icon: string;
  x: number;
  y: number;
  width?: number;
  height?: number;
  role?: ContainerRole;
  workloadType?: 'DEPLOYMENT' | 'STATEFULSET' | 'DAEMONSET';
  isAffected?: boolean;
  element?: HTMLElement;
  bundleMeta?: ConfigBundleMeta;
}

interface ConnectionPoint {
  side: 'top' | 'right' | 'bottom' | 'left';
  x: number;
  y: number;
}

interface DiagramLink {
  id: string;
  from: string;
  to: string;
  fromPoint?: ConnectionPoint;
  toPoint?: ConnectionPoint;
  element?: SVGElement;
}

interface AiSuggestedNode {
  type: string;
  name: string;
  description?: string;
  links?: { target: string; type?: string }[];
}

interface ChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
  pending?: boolean;
  error?: boolean;
}

@Component({
  selector: 'app-diagram',
  templateUrl: './diagram.component.html',
  styleUrls: ['./diagram.component.scss']
})
export class DiagramComponent implements OnInit, OnDestroy, AfterViewInit {

  @ViewChild('container', { static: true }) container!: ElementRef;
  @ViewChild('diagramCanvas', { static: true }) diagramCanvas!: ElementRef;
  @ViewChild('diagramGrid', { static: true }) diagramGrid!: ElementRef<HTMLDivElement>;
  @ViewChild('diagramSurface', { static: true }) diagramSurface!: ElementRef;
  @ViewChild('paletteContainer', { static: true }) paletteContainer!: ElementRef;
  @ViewChild('podShellOverlay') podShellOverlay?: OverlayPanel;
  @ViewChild('minimapSvg') minimapSvg?: ElementRef<SVGElement>;

  nodes: DiagramNode[] = [];
  links: DiagramLink[] = [];
  selectedNode: DiagramNode | null = null;
  selectedLink: DiagramLink | null = null;
  isConnecting: boolean = false;
  connectionStartNode: DiagramNode | null = null;
  connectionStartPoint: ConnectionPoint | null = null;
  isDraggingConnection: boolean = false;
  tempLine: SVGLineElement | null = null;
  isResizing: boolean = false;
  undoStack: { nodes: DiagramNode[], links: DiagramLink[], rawManifests: any[] }[] = [];
  maxUndoSteps: number = 20;
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
  aiDialogVisible = false;
  aiPrompt = '';
  aiLoading = false;
  aiSuggestions: AiSuggestedNode[] = [];
  aiError: string | null = null;
  chatDialogVisible = false;
  chatMessages: ChatMessage[] = [];
  chatInput = '';
  chatLoading = false;
  lastAssistantMessage: ChatMessage | null = null;
  importingFromChat = false;
  connectionHelpText = 'Trascina per collegare questo blocco con la sua dipendenza UML';
  clusterYamlDialogVisible = false;
  clusterYamlMode: 'import' | 'export' = 'import';
  clusterYamlText = '';
  clusterYamlError: string | null = null;
  clusterYamlLoading = false;
  clusterYamlFileName = '';
  clusterExportMode: ClusterExportMode = 'FLAT_YAML';
  clusterExportModeOptions = [
    { label: 'Flat YAML', value: 'FLAT_YAML' as ClusterExportMode },
    { label: 'Helm Chart (.zip)', value: 'HELM_CHART' as ClusterExportMode }
  ];
  helmChartBlob: Blob | null = null;
  @ViewChild('yamlFileInput') yamlFileInput?: ElementRef<HTMLInputElement>;
  private currentClusterSnapshot: Cluster | null = null;
  private rawManifests: any[] = [];
  readonly nodeContentSize = 80;
  readonly nodeBorderWidth = 3;
  private readonly surfacePadding = 200;
  surfaceWidth = 1600;
  surfaceHeight = 1200;
  viewportRect = { x: 0, y: 0, width: 0, height: 0 };
  viewportState = { offsetX: 0, offsetY: 0, scale: 1 };
  viewportTransform = 'translate(0px, 0px) scale(1)';
  handMode = false;
  isPanning = false;
  private panStart = { x: 0, y: 0, offsetX: 0, offsetY: 0 };
  minimapVisible = true;
  readonly minimapSize = { width: 240, height: 170 };
  private readonly minimapPreferenceKey = 'diagram:minimap:visible';
  private isDraggingMinimap = false;
  private panPointerMove?: (event: PointerEvent) => void;
  private panPointerUp?: (event: PointerEvent) => void;
  private readonly dependencyPriority: Record<string, number> = {
    ingress: 6,
    service: 5,
    deployment: 4,
    job: 4,
    container: 2,
    volume: 1,
    configmap: 1,
    secret: 1
  };
  private readonly fallbackIconType = 'container';
  private readonly connectionCaptureRadius = 28;
  podMenuPods: PodSummary[] = [];
  podMenuLoading = false;
  podMenuError: string | null = null;
  private podMenuContext: { namespace: string; deploymentName: string } | null = null;
  podShellDialogVisible = false;
  activeShellTarget: { namespace: string; podName: string; containerName?: string } | null = null;

  constructor(
    private iconService: IconService,
    private store: Store,
    private diagramService: DiagramService,
    private aiAssistantService: AiAssistantService,
    private notificationService: NotificationService,
    private podShellService: PodShellService,
    private configurationChangeService: ConfigurationChangeService,
    public resourceSyncService: ResourceSyncService,
    private zone: NgZone
  ) { }



  ngOnInit(): void {
    this.minimapVisible = this.restoreMinimapPreference();
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

    this.subscription.add(
      this.store.select(getCurrentCluster).pipe(
        tap(cluster => {
          this.currentClusterSnapshot = cluster ? Cluster.fromJSON(cluster) : null;
          this.syncDiagramNodeNames();
          this.syncContainerRolesFromCluster();
          this.syncWorkloadTypesFromCluster();
          this.syncAffectedStateFromCluster();
          this.syncConfigBundleMetaFromCluster();
        })
      ).subscribe()
    );
  }

  private initializeInteract(): void {
    interact('.diagram-node').unset();
    interact('.connection-point').unset();

    interact('.diagram-node').draggable({
      listeners: {
        start: () => this.zone.run(() => this.saveToUndoStack()),
        move: (event) => this.onNodeDragMove(event),
        end: () => this.zone.run(() => {
          this.updateDiagramData();
          this.updateSurfaceSize();
        })
      },
      inertia: false,
      ignoreFrom: '.connection-point'
    }).styleCursor(false);

  }

  private initializePanHandlers(): void {
    const viewportEl = this.diagramCanvas.nativeElement as HTMLElement;

    const onPointerDown = (event: PointerEvent) => {
      if (!this.shouldStartPan(event)) {
        return;
      }
      event.preventDefault();
      this.isPanning = true;
      this.panStart = {
        x: event.clientX,
        y: event.clientY,
        offsetX: this.viewportState.offsetX,
        offsetY: this.viewportState.offsetY
      };
      this.setPanActive(true);
      viewportEl.setPointerCapture(event.pointerId);
    };

    this.panPointerMove = (event: PointerEvent) => {
      if (!this.isPanning) {
        return;
      }
      const dx = (event.clientX - this.panStart.x) / (this.viewportState.scale || 1);
      const dy = (event.clientY - this.panStart.y) / (this.viewportState.scale || 1);
      this.viewportState.offsetX = this.panStart.offsetX + dx;
      this.viewportState.offsetY = this.panStart.offsetY + dy;
      this.applyViewportTransform();
      this.zone.run(() => this.updateViewportRect());
    };

    this.panPointerUp = (event: PointerEvent) => {
      if (this.isPanning) {
        this.isPanning = false;
        this.setPanActive(false);
      }
      if (viewportEl.hasPointerCapture(event.pointerId)) {
        viewportEl.releasePointerCapture(event.pointerId);
      }
    };

    viewportEl.addEventListener('pointerdown', onPointerDown);
    viewportEl.addEventListener('pointermove', this.panPointerMove);
    viewportEl.addEventListener('pointerup', this.panPointerUp);
    viewportEl.addEventListener('pointercancel', this.panPointerUp);
    this.updatePanCursor();
  }

  ngAfterViewInit(): void {
    this.zone.runOutsideAngular(() => {
      this.initializeInteract();
      this.initializePanHandlers();
    });
  }


  private initializeDiagram() {
    const canvas = this.diagramCanvas.nativeElement;
    // Create SVG for links
    this.svgElement = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    this.svgElement.classList.add('diagram-links-layer');
    this.svgElement.style.position = 'absolute';
    this.svgElement.style.top = '0';
    this.svgElement.style.left = '0';
    this.svgElement.style.width = `${this.surfaceWidth}px`;
    this.svgElement.style.height = `${this.surfaceHeight}px`;
    this.svgElement.style.pointerEvents = 'auto';
    this.svgElement.style.zIndex = '1';
    this.diagramSurface.nativeElement.appendChild(this.svgElement);
    this.ensureArrowMarker();

    // Render existing links
    this.renderLinks();
    this.updateViewportRect();
    this.applyViewportTransform();
  }

  onCanvasDrop(event: DropEvent) {
    const baseName = event.data.baseName || event.data.name;
    const coords = this.relativeToDiagram(event.x, event.y);
    this.createNode(event.data.type, baseName, event.data.icon, coords.x, coords.y);
  }

  private initializePaletteItems() {
    this.paletteItems = this.createNodes();
  }

  onCanvasScroll(): void {
    this.updateViewportRect();
  }

  private onNodeDragMove(event: any): void {
    if (this.isConnecting) {
      return;
    }
    const target = event.target as HTMLElement;
    const nodeId = target?.getAttribute('data-node-id');
    if (!nodeId) {
      return;
    }
    const node = this.nodes.find(n => n.id === nodeId);
    if (!node) {
      return;
    }

    const scale = this.viewportState.scale || 1;
    node.x += event.dx / scale;
    node.y += event.dy / scale;

    target.style.transform = '';
    target.style.left = `${node.x}px`;
    target.style.top = `${node.y}px`;
    this.updateLinks();
    this.updateViewportRect();
  }

  private onNodeResize(event: any): void {
    const target = event.target as HTMLElement;
    const nodeId = target?.getAttribute('data-node-id');
    if (!nodeId) {
      return;
    }
    const node = this.nodes.find(n => n.id === nodeId);
    if (!node) {
      return;
    }

    const delta = event.deltaRect || { left: 0, top: 0 };
    node.x += delta.left / this.viewportState.scale;
    node.y += delta.top / this.viewportState.scale;
    node.width = Math.max(60, event.rect.width / this.viewportState.scale);
    node.height = Math.max(60, event.rect.height / this.viewportState.scale);

    target.style.transform = '';
    target.style.left = `${node.x}px`;
    target.style.top = `${node.y}px`;
    target.style.width = `${node.width}px`;
    target.style.height = `${node.height}px`;

    this.updateLinks();
    this.updateViewportRect();
  }

  onConnectionPointMouseDown(event: MouseEvent, node: DiagramNode, point: ConnectionPoint): void {
    event.stopPropagation();
    event.preventDefault();

    this.isConnecting = true;
    this.isDraggingConnection = true;
    this.connectionStartNode = node;
    this.connectionStartPoint = point;
    this.createTempLine(point.x, point.y, point.x, point.y);

    const onMove = (moveEvent: MouseEvent) => {
      if (!this.isConnecting || !this.tempLine) {
        return;
      }
      const diagramCoords = this.screenToDiagram(moveEvent.clientX, moveEvent.clientY);
      this.tempLine.setAttribute('x2', diagramCoords.x.toString());
      this.tempLine.setAttribute('y2', diagramCoords.y.toString());
      this.highlightNearbyConnectionPoints(moveEvent.clientX, moveEvent.clientY);
    };

    const onUp = (upEvent: MouseEvent) => {
      if (this.isConnecting) {
        const connectionPoint = (upEvent.target as HTMLElement)?.closest?.('.connection-point');
        if (connectionPoint) {
          const nodeId = connectionPoint.getAttribute('data-node-id');
          const targetNode = this.nodes.find(n => n.id === nodeId);
          if (targetNode && targetNode.id !== this.connectionStartNode?.id) {
            const points = this.getConnectionPoints(targetNode);
            const targetPoint = this.findClosestConnectionPoint(upEvent.clientX, upEvent.clientY, points);
            this.createLinkWithPoints(
              this.connectionStartNode!.id,
              targetNode.id,
              this.connectionStartPoint!,
              targetPoint
            );
          }
        } else {
          const nearest = this.findNearestDroppablePoint(upEvent.clientX, upEvent.clientY, this.connectionStartNode?.id);
          if (nearest) {
            this.createLinkWithPoints(
              this.connectionStartNode!.id,
              nearest.node.id,
              this.connectionStartPoint!,
              nearest.point
            );
          }
        }
      }
      this.cancelConnection();
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };

    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  }

  private getClientCoords(event: any): { x: number; y: number } {
    if (event?.client) {
      return { x: event.client.x, y: event.client.y };
    }
    if (typeof event?.clientX === 'number' && typeof event?.clientY === 'number') {
      return { x: event.clientX, y: event.clientY };
    }
    return { x: 0, y: 0 };
  }

  openAiDialog(): void {
    this.aiPrompt = '';
    this.aiSuggestions = [];
    this.aiError = null;
    this.aiDialogVisible = true;
  }

  closeAiDialog(): void {
    if (this.aiLoading) {
      return;
    }
    this.aiDialogVisible = false;
  }

  submitAiPrompt(): void {
    if (!this.aiPrompt || !this.aiPrompt.trim()) {
      this.notificationService.warn('Add an instruction', 'Describe the blocks you want the assistant to create.');
      return;
    }

    this.aiLoading = true;
    this.aiError = null;
    this.aiSuggestions = [];

    this.aiAssistantService.generate({
      task: 'diagram_nodes',
      prompt: this.aiPrompt.trim(),
      context: this.buildDiagramContext(),
      format: 'json'
    }).subscribe({
      next: response => {
        this.handleAiSuggestions(response.content);
        this.aiLoading = false;
      },
      error: error => {
        const detail = error?.error || error?.message || 'Local AI request failed.';
        this.aiError = typeof detail === 'string' ? detail : 'Local AI request failed.';
        this.notificationService.error('AI request failed', this.aiError || undefined);
        this.aiLoading = false;
      }
    });
  }

  applyAiSuggestions(): void {
    if (!this.aiSuggestions.length) {
      this.notificationService.warn('Nothing to add', 'Ask the assistant to produce blocks before applying.');
      return;
    }

    const nodeSpacingX = 150;
    const nodeSpacingY = 140;
    const startX = 120;
    const startY = 120;

    this.saveToUndoStack();

    const createdNodes = new Map<string, DiagramNode>();
    this.aiSuggestions.forEach((suggestion, index) => {
      const normalizedType = suggestion.type?.toLowerCase?.() || suggestion.type;
      const icon = this.iconService.getIconPath(normalizedType);
      if (!icon) {
        this.notificationService.warn('Unsupported block type', `Skipping ${suggestion.name} (${normalizedType}).`);
        return;
      }

      const column = index % 3;
      const row = Math.floor(index / 3);
      const x = startX + column * nodeSpacingX;
      const y = startY + row * nodeSpacingY;

      const node = this.createNode(
        normalizedType,
        normalizedType,
        icon,
        x,
        y,
        { preferredName: suggestion.name, skipUndo: true, deferUpdate: true }
      );

      createdNodes.set(suggestion.name, node);
    });

    this.updateDiagramData();

    // Create links based on AI suggestions
    this.aiSuggestions.forEach(suggestion => {
      if (!suggestion.links?.length) {
        return;
      }
      const sourceNode = createdNodes.get(suggestion.name);
      if (!sourceNode) {
        return;
      }

      const sourcePoints = this.getConnectionPoints(sourceNode);
      const defaultSourcePoint = sourcePoints.find(point => point.side === 'right') || sourcePoints[0];

      suggestion.links.forEach(link => {
        const targetNode = createdNodes.get(link.target);
        if (!targetNode) {
          return;
        }
        const targetPoints = this.getConnectionPoints(targetNode);
        const defaultTargetPoint = targetPoints.find(point => point.side === 'left') || targetPoints[0];
        this.createLinkWithPoints(
          sourceNode.id,
          targetNode.id,
          defaultSourcePoint,
          defaultTargetPoint,
          { skipUndo: true, deferUpdate: true }
        );
      });
    });

    this.updateDiagramData();
    this.notificationService.success('Diagram updated', 'AI generated blocks were added to the canvas.');
    this.aiDialogVisible = false;
    this.aiSuggestions = [];
    this.aiPrompt = '';
  }

  openClusterYamlDialog(mode: 'import' | 'export'): void {
    if (mode === 'export' && !this.isExportAllowed()) {
      this.notificationService.warn('Template required', 'Generate the template before exporting YAML.');
      this.clusterYamlDialogVisible = false;
      return;
    }
    this.clusterYamlMode = mode;
    this.clusterYamlError = null;
    this.clusterYamlText = '';
    this.clusterYamlLoading = false;
    this.clusterYamlDialogVisible = true;

    if (mode === 'export') {
      this.clusterExportMode = 'FLAT_YAML';
      this.clusterYamlFileName = '';
      this.helmChartBlob = null;
      this.fetchClusterExport();
    } else {
      this.clusterYamlText = '';
      this.clusterYamlFileName = '';
      this.helmChartBlob = null;
    }
  }

  private isExportAllowed(): boolean {
    return this.currentClusterSnapshot?.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT;
  }

  private fetchClusterExport(): void {
    this.clusterYamlLoading = true;
    this.clusterYamlError = null;

    this.store.select(getCurrentCluster).pipe(take(1)).subscribe(cluster => {
      if (!cluster) {
        this.notificationService.warn('No diagram to export', 'Create or load a diagram before exporting YAML.');
        this.clusterYamlDialogVisible = false;
        this.clusterYamlLoading = false;
        return;
      }

      const payload = JSON.parse(JSON.stringify(cluster));
      const sanitizedName = this.sanitizeFileName(cluster?.name || 'izykube-namespace');

      if (this.clusterExportMode === 'HELM_CHART') {
        this.aiAssistantService.exportHelmChart(payload).pipe(
          finalize(() => this.clusterYamlLoading = false)
        ).subscribe({
          next: (response: AiHelmChartExportResponse) => {
            this.clusterYamlText = '';
            this.helmChartBlob = response.blob;
            const fallbackName = `${sanitizedName}-chart.zip`;
            this.clusterYamlFileName = response.fileName || fallbackName;
          },
          error: (error) => {
            this.handleClusterExportError(error);
          }
        });
        return;
      }

      this.aiAssistantService.exportYaml(payload).pipe(
        finalize(() => this.clusterYamlLoading = false)
      ).subscribe({
        next: (response: AiExportYamlResponse) => {
          this.helmChartBlob = null;
          this.clusterYamlText = response.yaml;
          this.clusterYamlFileName = `${sanitizedName}.yaml`;
        },
        error: (error) => {
          this.handleClusterExportError(error);
        }
      });
    });
  }

  private handleClusterExportError(error: any): void {
    const detail = error?.error || error?.message || 'Diagram export failed.';
    this.notificationService.error('Export failed', typeof detail === 'string' ? detail : undefined);
    this.clusterYamlDialogVisible = false;
  }

  handleExportModeChange(mode: ClusterExportMode): void {
    if (this.clusterExportMode === mode) {
      return;
    }
    this.clusterExportMode = mode;
    this.clusterYamlText = '';
    this.clusterYamlFileName = '';
    this.clusterYamlError = null;
    this.helmChartBlob = null;
    this.fetchClusterExport();
  }

  importClusterYaml(): void {
    const yaml = this.clusterYamlText?.trim();
    if (!yaml) {
      this.notificationService.warn('Add YAML', 'Paste diagram YAML before importing.');
      return;
    }

    this.clusterYamlLoading = true;
    this.aiAssistantService.importYaml({
      yaml,
      name: this.currentClusterSnapshot?.name ?? undefined
    }).pipe(
      finalize(() => this.clusterYamlLoading = false)
    ).subscribe({
      next: (response: AiImportYamlResponse) => {
        this.applyImportedCluster(response);
        this.notificationService.success('Diagram imported', 'Diagram updated from YAML.');
        this.clusterYamlDialogVisible = false;
      },
      error: (error) => {
        const detail = error?.error || error?.message || 'Diagram import failed.';
        this.clusterYamlError = typeof detail === 'string' ? detail : 'Diagram import failed.';
      }
    });
  }

  copyExportedYaml(): void {
    if (!this.clusterYamlText) {
      return;
    }
    if (navigator && navigator.clipboard) {
      navigator.clipboard.writeText(this.clusterYamlText).then(
        () => this.notificationService.success('Copied', 'Namespace YAML copied to clipboard.'),
        () => this.notificationService.error('Copy failed', 'Unable to copy YAML to clipboard.')
      );
    } else {
      this.notificationService.warn('Clipboard unavailable', 'Copy not supported in this environment.');
    }
  }

  downloadExportedYaml(): void {
    if (!this.clusterYamlText) {
      return;
    }
    const blob = new Blob([this.clusterYamlText], { type: 'text/yaml;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = this.clusterYamlFileName || 'izykube-namespace.yaml';
    link.click();
    URL.revokeObjectURL(url);
  }

  downloadHelmChart(): void {
    if (!this.helmChartBlob) {
      return;
    }
    const url = URL.createObjectURL(this.helmChartBlob);
    const link = document.createElement('a');
    link.href = url;
    link.download = this.clusterYamlFileName || `${this.sanitizeFileName(this.currentClusterSnapshot?.name || 'izykube-namespace')}-chart.zip`;
    link.click();
    URL.revokeObjectURL(url);
  }

  openChatDialog(): void {
    this.chatDialogVisible = true;
    if (!this.chatMessages.length) {
      this.chatMessages.push({
        role: 'system',
        content: 'How can I help with your Kubernetes architecture today?',
        timestamp: new Date()
      });
    }
  }

  closeChatDialog(): void {
    if (this.chatLoading) {
      return;
    }
    this.chatDialogVisible = false;
  }

  sendChatMessage(): void {
    if (!this.chatInput || !this.chatInput.trim()) {
      return;
    }

    const content = this.chatInput.trim();
    const timestamp = new Date();
    const userMessage: ChatMessage = {
      role: 'user',
      content,
      timestamp
    };
    this.chatMessages.push(userMessage);

    this.chatInput = '';
    this.chatLoading = true;

    const history: AiChatMessage[] = this.chatMessages
      .filter(message => message.role !== 'system' || message.content.trim() !== '')
      .map(message => ({
        role: message.role as AiChatMessage['role'],
        content: message.content
      }));

    this.aiAssistantService.chat({
      task: 'diagram_helper',
      messages: history,
      context: this.buildChatContext()
    }).subscribe({
      next: response => {
        (response.messages || []).forEach(msg => {
          const assistantMessage: ChatMessage = {
            role: msg.role,
            content: msg.content,
            timestamp: new Date()
          };
          this.chatMessages.push(assistantMessage);
          if (assistantMessage.role === 'assistant') {
            this.lastAssistantMessage = assistantMessage;
          }
        });
        if (!response.messages?.length) {
          this.chatMessages.push({
            role: 'assistant',
            content: 'I could not generate a reply. Please try again.',
            timestamp: new Date(),
            error: true
          });
        }
        this.chatLoading = false;
      },
      error: error => {
        const detail = error?.error || error?.message || 'Local AI chat request failed.';
        this.chatMessages.push({
          role: 'assistant',
          content: typeof detail === 'string' ? detail : 'Local AI chat request failed.',
          timestamp: new Date(),
          error: true
        });
        this.notificationService.error('Chat failed', typeof detail === 'string' ? detail : undefined);
        this.chatLoading = false;
      }
    });
  }

  private buildDiagramContext(): string {
    const context = {
      nodes: this.nodes.map(node => ({ name: node.name, type: node.type })),
      links: this.links.map(link => ({ from: link.from, to: link.to }))
    };
    return JSON.stringify(context, null, 2);
  }

  private buildChatContext(): string {
    const selected = this.selectedNode
      ? {
        id: this.selectedNode.id,
        name: this.selectedNode.name,
        type: this.selectedNode.type
      }
      : null;

    return JSON.stringify({
      selectedNode: selected,
      nodes: this.nodes.map(node => ({ id: node.id, name: node.name, type: node.type })),
      links: this.links.map(link => ({ from: link.from, to: link.to }))
    });
  }

  canImportLastAssistantReply(): boolean {
    if (!this.lastAssistantMessage || this.lastAssistantMessage.role !== 'assistant') {
      return false;
    }
    const yaml = this.extractYamlFromMessage(this.lastAssistantMessage.content);
    return yaml.length > 0;
  }

  importLastAssistantMessage(): void {
    if (!this.canImportLastAssistantReply() || !this.lastAssistantMessage) {
      return;
    }
    const yaml = this.extractYamlFromMessage(this.lastAssistantMessage.content);
    if (!yaml) {
      this.notificationService.warn('No YAML found', 'Ask the assistant to provide YAML before importing.');
      return;
    }

    this.importingFromChat = true;
    this.aiAssistantService.importYaml({ yaml, name: 'AI Generated Diagram' }).subscribe({
      next: (response) => {
        this.applyImportedCluster(response);
        this.notificationService.success('Diagram imported', 'Diagram updated from YAML.');
        this.importingFromChat = false;
      },
      error: (error) => {
        const detail = error?.error || error?.message || 'YAML import failed.';
        this.notificationService.error('Import failed', typeof detail === 'string' ? detail : undefined);
        this.importingFromChat = false;
      }
    });
  }

  private extractYamlFromMessage(content: string): string {
    if (!content) {
      return '';
    }
    const fencedMatch = content.match(/```(?:yaml|yml)?\s*([\s\S]*?)```/i);
    if (fencedMatch && fencedMatch[1]) {
      return fencedMatch[1].trim();
    }
    return content.trim();
  }

  private applyImportedCluster(imported: AiImportYamlResponse): void {
    const cluster = Cluster.fromJSON(imported);
    this.store.dispatch(actions.loadCluster({ cluster }));

    this.clearDiagram();
    this.nodes = [];
    this.links = [];
    this.undoStack = [];
    this.selectedNode = null;
    this.selectedLink = null;
    this.rawManifests = [];

    if (cluster.diagram) {
      this.loadDiagramData(cluster.diagram);
      if ((!this.links || this.links.length === 0) && Array.isArray(cluster.links) && cluster.links.length) {
        this.links = this.rebuildLinksFromClusterLinks(cluster.links as Link[]);
      }
    } else {
      const snapshot = this.buildDiagramFromClusterData(cluster);
      this.nodes = snapshot.nodes;
      this.links = snapshot.links;
      this.enforceUmlDependencyOrientationOnLinks();
      this.rawManifests = [];
    }

    this.syncConfigBundleMetaFromCluster();
    this.renderLinks();
    this.updateLinkStyles();
    this.diagramService.clearSelectedNode();
  }

  onYamlFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) {
      return;
    }

    const file = input.files[0];
    this.clusterYamlFileName = file.name;
    const reader = new FileReader();

    reader.onload = () => {
      const result = reader.result;
      this.clusterYamlText = typeof result === 'string'
        ? result
        : new TextDecoder().decode(result as ArrayBuffer);
    };

    reader.onerror = () => {
      this.notificationService.error('File read failed', 'Unable to read the selected YAML file.');
    };

    reader.readAsText(file);
    input.value = '';
  }

  private sanitizeFileName(value: string): string {
    return value
      .toLowerCase()
      .replace(/[^a-z0-9-]+/g, '-')
      .replace(/^-+|-+$/g, '') || 'izykube-namespace';
  }

  private handleAiSuggestions(content: string): void {
    try {
      const parsed = JSON.parse(content);
      if (!parsed || !Array.isArray(parsed.nodes)) {
        throw new Error('Response does not include a nodes array.');
      }

      const suggestions: AiSuggestedNode[] = parsed.nodes
        .filter((node: any) => node && node.type && node.name)
        .map((node: any) => ({
          type: String(node.type).toLowerCase(),
          name: String(node.name),
          description: node.description ? String(node.description) : undefined,
          links: Array.isArray(node.links) ? node.links.map((link: any) => ({
            target: String(link.target ?? ''),
            type: link.type ? String(link.type) : undefined
          })).filter((link: any) => link.target) : []
        }));

      if (!suggestions.length) {
        this.aiSuggestions = [];
        this.aiError = 'The assistant did not return any valid nodes.';
        return;
      }

      this.aiSuggestions = suggestions;
      this.aiError = null;
    } catch (error: any) {
      this.aiSuggestions = [];
      this.aiError = 'Failed to parse AI response. Ensure the local model returns valid JSON.';
      this.notificationService.error('Invalid AI response', this.aiError);
    }
  }

  private createNode(
    type: string,
    baseName: string,
    icon: string,
    x: number,
    y: number,
    options?: { preferredName?: string; skipUndo?: boolean; deferUpdate?: boolean }
  ): DiagramNode {
    if (!options?.skipUndo) {
      this.saveToUndoStack();
    }

    const normalizedType = type.toLowerCase();
    const resolvedName = options?.preferredName
      ? this.ensureUniqueName(options.preferredName)
      : this.generateUniqueName(baseName);

    const node: DiagramNode = {
      id: uuidv4(),
      name: resolvedName,
      type: normalizedType,
      icon: icon,
      x: x,
      y: y,
      width: this.nodeContentSize,
      height: this.nodeContentSize,
      ...(normalizedType === 'deployment' ? { workloadType: 'DEPLOYMENT' as DiagramNode['workloadType'] } : {})
    };

    this.nodes.push(node);
    this.diagramService.addClusterNode(type, node.id, resolvedName);

    if (!options?.deferUpdate) {
      this.updateDiagramData();
    }
    this.updateSurfaceSize();

    return node;
  }

  private ensureUniqueName(desiredName: string): string {
    if (!this.nodes.some(node => node.name === desiredName)) {
      return desiredName;
    }

    let suffix = 1;
    let candidate = `${desiredName}-${suffix}`;
    while (this.nodes.some(node => node.name === candidate)) {
      suffix += 1;
      candidate = `${desiredName}-${suffix}`;
    }
    return candidate;
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

  private normalizeDiagramNode(rawNode: any, overrides?: Partial<DiagramNode>): DiagramNode {
    const type = (rawNode?.type || rawNode?.kind || this.fallbackIconType).toLowerCase();
    const normalized: DiagramNode = {
      id: rawNode?.id || uuidv4(),
      name: rawNode?.name || type,
      type,
      icon: rawNode?.icon || this.resolveIconPath(type),
      x: typeof rawNode?.x === 'number' ? rawNode.x : 0,
      y: typeof rawNode?.y === 'number' ? rawNode.y : 0,
      width: typeof rawNode?.width === 'number' ? rawNode.width : this.nodeContentSize,
      height: typeof rawNode?.height === 'number' ? rawNode.height : this.nodeContentSize,
      isAffected: !!rawNode?.isAffected,
      ...overrides
    };

    if (type === 'deployment') {
      const workloadSource = (overrides?.workloadType || rawNode?.workloadType) as string | undefined;
      normalized.workloadType = workloadSource ? (workloadSource.toString().toUpperCase() as DiagramNode['workloadType']) : 'DEPLOYMENT';
    }

    if (type === 'container') {
      const overrideRole = overrides?.role;
      const rawRole = overrideRole ?? rawNode?.role;
      const normalizedRole = toContainerRole(rawRole);
      if (normalizedRole) {
        normalized.role = normalizedRole;
      } else {
        delete normalized.role;
      }
    } else if ('role' in normalized) {
      delete normalized.role;
    }

    return normalized;
  }

  private createNodes(): DragDropData[] {
    return [
      { name: 'ingress', type: 'ingress', icon: this.iconService.getIconPath('ingress') },
      { name: 'Istio', type: 'istio', icon: this.iconService.getIconPath('istio') },
      { name: 'container', type: 'container', icon: this.iconService.getIconPath('container') },
      { name: 'deployment', type: 'deployment', icon: this.iconService.getIconPath('deployment') },
      { name: 'service', type: 'service', icon: this.iconService.getIconPath('service') },
      { name: 'configbundle', displayName: 'Config bundle', baseName: 'config-bundle', type: 'configmap', icon: this.iconService.getIconPath('configmap') },
      { name: 'volume', type: 'volume', icon: this.iconService.getIconPath('volume') },
      { name: 'job', type: 'job', icon: this.iconService.getIconPath('job') }
    ];
  }

  getContainerBadge(node: DiagramNode): { label: string; cssClass: string; title: string } | null {
    if (node.type !== 'container') {
      return null;
    }

    const role = this.resolveContainerRole(node);
    switch (role) {
      case 'INIT':
        return { label: 'I', cssClass: 'diagram-node__badge--init', title: 'Init container' };
      case 'SIDECAR':
        return { label: 'S', cssClass: 'diagram-node__badge--sidecar', title: 'Sidecar container' };
      default:
        return null;
    }
  }

  getWorkloadBadge(node: DiagramNode): { label: string; title: string } | null {
    if (node.type !== 'deployment') {
      return null;
    }
    const workload = (node.workloadType || 'DEPLOYMENT').toUpperCase();
    if (workload === 'STATEFULSET') {
      return { label: 'SS', title: 'StatefulSet workload' };
    }
    if (workload === 'DAEMONSET') {
      return { label: 'DS', title: 'DaemonSet workload' };
    }
    return null;
  }

  shouldShowSecretBadge(node: DiagramNode): boolean {
    const type = node.type?.toLowerCase();
    if (type !== 'configmap' && type !== 'secret' && type !== 'configbundle') {
      return false;
    }
    return !!node.bundleMeta?.hasSecretEntries;
  }

  getConfigBundleBadgeTitle(node: DiagramNode): string {
    if (!node.bundleMeta) {
      return '';
    }
    if (node.bundleMeta.hasSecretEntries && node.bundleMeta.hasPlainEntries) {
      return 'Contains plain and secret entries';
    }
    if (node.bundleMeta.hasSecretEntries) {
      return 'Contains secret entries';
    }
    return 'Contains plain entries';
  }

  getConfigBundleBadgeClass(node: DiagramNode): string {
    if (!node.bundleMeta) {
      return '';
    }
    if (node.bundleMeta.hasSecretEntries && node.bundleMeta.hasPlainEntries) {
      return 'diagram-node__secret-badge--mixed';
    }
    if (node.bundleMeta.hasSecretEntries) {
      return 'diagram-node__secret-badge--secret';
    }
    return 'diagram-node__secret-badge--plain';
  }

  shouldShowPodShellTrigger(node: DiagramNode): boolean {
    return node.type?.toLowerCase() === 'deployment' && this.isNamespaceDeployed();
  }

  onPodShellIconClick(event: MouseEvent, node: DiagramNode): void {
    event.stopPropagation();
    event.preventDefault();
    if (!this.shouldShowPodShellTrigger(node)) {
      return;
    }

    const namespace = this.currentClusterSnapshot?.nameSpace;
    if (!namespace) {
      this.notificationService.warn('Namespace required', 'Assign a namespace before opening a pod shell.');
      return;
    }

    this.podMenuContext = { namespace, deploymentName: node.name };
    this.podMenuLoading = true;
    this.podMenuError = null;
    this.podMenuPods = [];

    this.podShellService.getPodsByDeployment(namespace, node.name).pipe(
      finalize(() => this.podMenuLoading = false)
    ).subscribe({
      next: pods => {
        this.podMenuPods = pods;
        this.showPodShellOverlay(event);
      },
      error: () => {
        this.podMenuError = 'Unable to load pods for this deployment.';
        this.showPodShellOverlay(event);
      }
    });
  }

  openShellForPod(pod: PodSummary): void {
    if (!this.podMenuContext) {
      return;
    }

    this.activeShellTarget = {
      namespace: this.podMenuContext.namespace,
      podName: pod.name
    };
    this.podShellDialogVisible = true;
    this.podShellOverlay?.hide();
  }

  onShellDialogClosed(): void {
    this.podShellDialogVisible = false;
    this.activeShellTarget = null;
  }

  private showPodShellOverlay(event: MouseEvent): void {
    if (!this.podShellOverlay) {
      return;
    }
    this.podShellOverlay.hide();
    this.podShellOverlay.show(event);
  }

  private isNamespaceDeployed(): boolean {
    return this.currentClusterSnapshot?.status === ClusterStatusEnum.DEPLOYED;
  }

  private resolveContainerRole(node: DiagramNode): ContainerRole | undefined {
    if (node.type !== 'container') {
      return undefined;
    }

    const inlineRole = node.role ? toContainerRole(node.role) : undefined;
    if (inlineRole) {
      return inlineRole;
    }

    const snapshotNode = this.currentClusterSnapshot?.nodes?.find((n: any) => n.id === node.id);
    if (snapshotNode) {
      return toContainerRole((snapshotNode as any).role);
    }

    return undefined;
  }

  onNodeLabelEdit(node: DiagramNode, event: any) {
    node.name = event.target.textContent || node.name;
    const shouldAutoSync = this.isNamespaceDeployed();
    if (shouldAutoSync) {
      node.isAffected = true;
    }
    const payload = shouldAutoSync ? { name: node.name, isAffected: true } : { name: node.name };
    this.diagramService.updateClusterNodes(node.id, payload);
    this.updateDiagramData();
    if (shouldAutoSync) {
      this.configurationChangeService.emit({ resourceId: node.id });
    }
  }

  // Link rendering & selection flow:
  // - Links are rendered as SVG <line> elements inside svgElement with click handlers.
  // - Panning/zoom is applied on the parent surface, so link coordinates stay in diagram space.
  // - Connection creation uses dedicated mouse listeners on connection points and sets isConnecting;
  //   Interact.js node drag ignores events when isConnecting is true to avoid conflicts.
  private renderLinks() {
    if (!this.svgElement) {
      return;
    }

    // Keep markers but rebuild the link shapes to ensure click handlers stay in sync
    const existingDefs = this.svgElement.querySelector('defs');
    const defsClone = existingDefs ? existingDefs.cloneNode(true) : null;

    while (this.svgElement.firstChild) {
      this.svgElement.removeChild(this.svgElement.firstChild);
    }

    if (defsClone) {
      this.svgElement.appendChild(defsClone);
    }
    this.ensureArrowMarker();

    this.links.forEach(link => this.renderLink(link));
    this.updateLinkStyles();
  }

  private renderLink(link: DiagramLink) {
    if (!this.svgElement) {
      console.error('SVG element is not initialized.');
      return;
    }
    const fromNode = this.nodes.find(n => n.id === link.from);
    const toNode = this.nodes.find(n => n.id === link.to);

    if (!fromNode || !toNode) return;

    const { fromPoint, toPoint } = this.resolveDynamicAnchors(link, fromNode, toNode);

    this.ensureArrowMarker();
    const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    line.classList.add('diagram-link');
    line.setAttribute('data-link-id', link.id);
    line.setAttribute('x1', fromPoint.x.toString());
    line.setAttribute('y1', fromPoint.y.toString());
    line.setAttribute('x2', toPoint.x.toString());
    line.setAttribute('y2', toPoint.y.toString());
    line.setAttribute('stroke', this.selectedLink?.id === link.id ? '#ff4444' : 'lightblue');
    line.setAttribute('stroke-width', this.selectedLink?.id === link.id ? '4' : '3');
    line.setAttribute('marker-end', 'url(#arrowhead)');
    line.style.cursor = 'pointer';
    line.style.pointerEvents = 'stroke';

    // Add click event listener for link selection
    line.addEventListener('click', (event) => {
      event.stopPropagation();
      this.zone.run(() => this.selectLink(link));
    });

    this.svgElement.appendChild(line);
    link.element = line;
  }

  private ensureArrowMarker(): void {
    if (!this.svgElement) {
      return;
    }
    const existingMarker = this.svgElement.querySelector('marker#arrowhead');
    if (existingMarker) {
      return;
    }
    const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
    const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker');
    marker.setAttribute('id', 'arrowhead');
    marker.setAttribute('markerWidth', '4');
    marker.setAttribute('markerHeight', '4');
    marker.setAttribute('refX', '3.6');
    marker.setAttribute('refY', '2');
    marker.setAttribute('orient', 'auto');
    marker.setAttribute('markerUnits', 'strokeWidth');
    marker.setAttribute('viewBox', '0 0 4 4');
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', 'M0 0 L4 2 L0 4 z');
    path.setAttribute('fill', 'context-stroke');
    path.setAttribute('stroke', 'none');
    marker.appendChild(path);
    defs.appendChild(marker);
    this.svgElement.appendChild(defs);
  }

  private updateLinks() {
    this.links.forEach(link => {
      const fromNode = this.nodes.find(n => n.id === link.from);
      const toNode = this.nodes.find(n => n.id === link.to);

      if (fromNode && toNode && link.element) {
        const { fromPoint, toPoint } = this.resolveDynamicAnchors(link, fromNode, toNode);
        link.element.setAttribute('x1', fromPoint.x.toString());
        link.element.setAttribute('y1', fromPoint.y.toString());
        link.element.setAttribute('x2', toPoint.x.toString());
        link.element.setAttribute('y2', toPoint.y.toString());
      }
    });
    this.updateLinkStyles();
  }

  selectNode(node: DiagramNode) {
    this.selectedNode = node;
    this.selectedLink = null; // Clear link selection when selecting a node
    this.updateLinkStyles();
    this.store.select(getNodeById(node.id)).pipe(take(1)).subscribe(existingNode => {
      if (!existingNode) {
        this.diagramService.addClusterNode(node.type, node.id, node.name);
      }
      this.diagramService.setSelectedNode(node.id);
    });
  }

  selectLink(link: DiagramLink) {
    this.selectedLink = link;
    this.selectedNode = null; // Clear node selection when selecting a link
    this.updateLinkStyles();
    this.diagramService.clearSelectedNode();
  }

  clearSelection() {
    this.selectedNode = null;
    this.selectedLink = null;
    this.updateLinkStyles();
    this.diagramService.clearSelectedNode();
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
  onDiagramKeyDown(event: KeyboardEvent) {
    if (this.isEditingFormField(event.target)) {
      return;
    }

    if (event.key === 'Delete' || event.key === 'Backspace') {
      if (this.selectedLink) {
        this.deleteSelectedLink();
        event.preventDefault();
      } else if (this.selectedNode) {
        this.deleteSelectedNode();
        event.preventDefault();
      }
    } else if (event.ctrlKey && event.key === 'z') {
      this.undo();
      event.preventDefault();
    }
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.updateViewportRect();
  }

  private isEditingFormField(target: EventTarget | null): boolean {
    if (!target) {
      return false;
    }

    const element = target as HTMLElement;
    if (!element) {
      return false;
    }

    const tagName = element.tagName?.toLowerCase();
    const editableTags = ['input', 'textarea', 'select'];
    if (tagName && editableTags.includes(tagName)) {
      return true;
    }

    return element.isContentEditable || !!element.closest('[contenteditable="true"]');
  }

  private deleteSelectedLink() {
    if (!this.selectedLink) return;

    this.saveToUndoStack();

    // Remove the SVG element
    if (this.selectedLink.element) {
      this.selectedLink.element.remove();
    }

    // Remove from links array and re-render
    this.links = this.links.filter(link => link.id !== this.selectedLink!.id);
    this.selectedLink = null;
    this.renderLinks();
    this.updateDiagramData();
  }

  private deleteSelectedNode() {
    if (!this.selectedNode) return;

    this.saveToUndoStack();

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

    const nodeId = this.selectedNode!.id;

    // Remove node from array
    this.nodes = this.nodes.filter(node => node.id !== nodeId);
    this.diagramService.removeClusterNode(nodeId);
    this.clearSelection();
    this.updateDiagramData();
  }

  private loadDiagramData(diagramData: string) {
    try {
      const data = JSON.parse(diagramData);
      const parsedNodes = Array.isArray(data.nodes) ? data.nodes : [];
      this.nodes = parsedNodes
        .filter((node: any) =>
        ((node?.type || node?.kind || '') as string).toLowerCase() !== 'pod'
      )
        .map((node: any) => this.normalizeDiagramNode(node));

      const parsedLinks = Array.isArray(data.links) ? data.links : [];
      this.links = parsedLinks.filter((link: any) => {
        return link.from && link.to &&
          this.nodes.some(node => node.id === link.from) &&
          this.nodes.some(node => node.id === link.to);
      }) as DiagramLink[];
      this.enforceUmlDependencyOrientationOnLinks();
      if (Array.isArray(data.rawManifests)) {
        this.rawManifests = data.rawManifests;
      }
      this.syncConfigBundleMetaFromCluster();
      this.updateSurfaceSize();
    } catch (error) {
      console.error('Error loading diagram data:', error);
      this.nodes = [];
      this.links = [];
      this.rawManifests = [];
    }
  }

  private createLinkWithPoints(
    fromNodeId: string,
    toNodeId: string,
    fromPoint: ConnectionPoint,
    toPoint: ConnectionPoint,
    options?: { skipUndo?: boolean; deferUpdate?: boolean }
  ) {
    const fromNode = this.nodes.find(node => node.id === fromNodeId);
    const toNode = this.nodes.find(node => node.id === toNodeId);

    if (!fromNode || !toNode) {
      return;
    }

    if (!this.isContainerLinkAllowed(fromNode, toNode)) {
      this.notificationService.warn(
        'Invalid connection',
        'Containers can only be linked to Deployments or Config Bundles.'
      );
      return;
    }

    const oriented = this.orientLinkByDependency(fromNode, toNode, fromPoint, toPoint);
    const sourceId = oriented.fromNode.id;
    const targetId = oriented.toNode.id;

    // Check if link already exists (regardless of drawing direction)
    const existingLink = this.links.find(link =>
      (link.from === sourceId && link.to === targetId) ||
      (link.from === targetId && link.to === sourceId)
    );

    if (existingLink) {
      console.log('Link already exists between these nodes');
      return;
    }

    if (!options?.skipUndo) {
      this.saveToUndoStack();
    }

    const link: DiagramLink = {
      id: uuidv4(),
      from: sourceId,
      to: targetId,
      fromPoint: oriented.fromPoint,
      toPoint: oriented.toPoint
    };

    this.links.push(link);
    this.renderLink(link);
    if (!options?.deferUpdate) {
      this.updateDiagramData();
    }
  }

  private syncDiagramNodeNames(): void {
    if (!this.currentClusterSnapshot?.nodes?.length || !this.nodes?.length) {
      return;
    }

    const nodeMap = new Map<string, any>(
      this.currentClusterSnapshot.nodes.map((node: any) => [node.id, node])
    );

    let hasChanges = false;
    const updatedNodes = this.nodes.map(node => {
      const clusterNode = nodeMap.get(node.id);
      if (!clusterNode) {
        return node;
      }

      const clusterName = typeof clusterNode.name === 'string' ? clusterNode.name : '';
      if (clusterName && clusterName !== node.name) {
        hasChanges = true;
        return { ...node, name: clusterName };
      }

      return node;
    });

    if (!hasChanges) {
      return;
    }

    this.nodes = updatedNodes;
    if (this.selectedNode) {
      this.selectedNode = this.nodes.find(n => n.id === this.selectedNode!.id) ?? null;
    }
    this.updateDiagramData();
  }

  private syncContainerRolesFromCluster(): void {
    if (!this.currentClusterSnapshot?.nodes?.length || !this.nodes?.length) {
      return;
    }

    const roleMap = new Map<string, ContainerRole>();
    this.currentClusterSnapshot.nodes.forEach((node: any) => {
      const kind = (node?.kind ?? node?.type ?? '').toLowerCase();
      if (kind === 'container' && node?.id) {
        const normalizedRole = toContainerRole(node?.role);
        if (normalizedRole) {
          roleMap.set(node.id, normalizedRole);
        }
      }
    });

    const shouldClearRoles = this.nodes.some(node => node.type === 'container' && node.role);
    if (!roleMap.size && !shouldClearRoles) {
      return;
    }

    let hasChanges = false;
    const updatedNodes = this.nodes.map(node => {
      if (node.type !== 'container') {
        return node;
      }
      const mappedRole = roleMap.get(node.id);
      if (!mappedRole) {
        if (!node.role) {
          return node;
        }
        hasChanges = true;
        const clone = { ...node } as DiagramNode;
        delete (clone as any).role;
        return clone;
      }

      if (node.role === mappedRole) {
        return node;
      }

      hasChanges = true;
      return { ...node, role: mappedRole };
    });

    if (hasChanges) {
      this.nodes = updatedNodes;
      this.updateDiagramData();
    }
  }

  private syncWorkloadTypesFromCluster(): void {
    if (!this.currentClusterSnapshot?.nodes?.length || !this.nodes?.length) {
      return;
    }
    const map = new Map<string, DiagramNode['workloadType']>();
    this.currentClusterSnapshot.nodes.forEach((node: any) => {
      const kind = (node?.kind ?? node?.type ?? '').toLowerCase();
      if (kind !== 'deployment' || !node?.id) {
        return;
      }
      const workload = typeof node?.workloadType === 'string'
        ? node.workloadType.toUpperCase()
        : 'DEPLOYMENT';
      map.set(node.id, workload as DiagramNode['workloadType']);
    });
    if (!map.size) {
      return;
    }
    let hasChanges = false;
    const updatedNodes = this.nodes.map(node => {
      if (node.type !== 'deployment') {
        return node;
      }
      const workload = map.get(node.id);
      if (!workload || workload === node.workloadType) {
        return node;
      }
      hasChanges = true;
      return { ...node, workloadType: workload };
    });
    if (hasChanges) {
      this.nodes = updatedNodes;
      if (this.selectedNode) {
        this.selectedNode = this.nodes.find(n => n.id === this.selectedNode!.id) ?? null;
      }
      this.updateDiagramData();
    }
  }

  private syncAffectedStateFromCluster(): void {
    if (!this.currentClusterSnapshot?.nodes?.length || !this.nodes?.length) {
      return;
    }

    const affectedMap = new Map<string, boolean>();
    this.currentClusterSnapshot.nodes.forEach((node: any) => {
      if (node?.id) {
        affectedMap.set(node.id, !!node?.isAffected);
      }
    });

    if (!affectedMap.size) {
      const hasExistingFlags = this.nodes.some(node => node.isAffected);
      if (!hasExistingFlags) {
        return;
      }
    }

    let hasChanges = false;
    const updatedNodes = this.nodes.map(node => {
      const shouldBlink = affectedMap.get(node.id) ?? false;
      if (!!node.isAffected === shouldBlink) {
        return node;
      }
      hasChanges = true;
      return { ...node, isAffected: shouldBlink };
    });

    if (!hasChanges) {
      return;
    }

    this.nodes = updatedNodes;
    if (this.selectedNode) {
      this.selectedNode = this.nodes.find(n => n.id === this.selectedNode!.id) ?? null;
    }
    this.updateDiagramData();
  }

  private syncConfigBundleMetaFromCluster(): void {
    if (!this.currentClusterSnapshot?.nodes?.length || !this.nodes?.length) {
      return;
    }

    const metaMap = new Map<string, ConfigBundleMeta>();
    this.currentClusterSnapshot.nodes.forEach((node: any) => {
      const type = (node?.kind ?? node?.type ?? '').toLowerCase();
      if (type !== 'configmap' && type !== 'secret' && type !== 'configbundle') {
        return;
      }
      const bundle = node?.configBundle;
      const entries = Array.isArray(bundle?.entries)
        ? bundle.entries
        : Array.isArray(node?.entries)
          ? node.entries
          : [];
      const secretCount = entries.filter((entry: any) => (entry?.sensitivity ?? '').toUpperCase() === 'SECRET').length;
      const plainCount = entries.filter((entry: any) => (entry?.sensitivity ?? '').toUpperCase() !== 'SECRET').length;
      metaMap.set(node.id, {
        hasSecretEntries: secretCount > 0 || type === 'secret',
        hasPlainEntries: plainCount > 0 && type !== 'secret',
        entryCount: entries.length
      } as ConfigBundleMeta);
    });

    const shouldClearMeta = !metaMap.size && this.nodes.some(node => node.bundleMeta);
    if (!metaMap.size && !shouldClearMeta) {
      return;
    }

    let hasChanges = false;
    const updatedNodes = this.nodes.map(node => {
      const nodeType = node.type?.toLowerCase();
      const isConfigBundleNode = nodeType === 'configmap' || nodeType === 'secret' || nodeType === 'configbundle';
      if (!isConfigBundleNode) {
        if (node.bundleMeta) {
          const clone = { ...node } as DiagramNode;
          delete (clone as any).bundleMeta;
          hasChanges = true;
          return clone;
        }
        return node;
      }
      const meta = metaMap.get(node.id);
      if (!meta) {
        if (node.bundleMeta) {
          const clone = { ...node } as DiagramNode;
          delete (clone as any).bundleMeta;
          hasChanges = true;
          return clone;
        }
        return node;
      }
      const sameMeta = node.bundleMeta
        && node.bundleMeta.hasPlainEntries === meta.hasPlainEntries
        && node.bundleMeta.hasSecretEntries === meta.hasSecretEntries
        && node.bundleMeta.entryCount === meta.entryCount;
      if (sameMeta) {
        return node;
      }
      hasChanges = true;
      return { ...node, bundleMeta: meta };
    });

    if (!hasChanges) {
      return;
    }

    this.nodes = updatedNodes;
    if (this.selectedNode) {
      this.selectedNode = this.nodes.find(n => n.id === this.selectedNode!.id) ?? null;
    }
    this.updateDiagramData();
  }

  private isContainerLinkAllowed(nodeA: DiagramNode, nodeB: DiagramNode): boolean {
    const typeA = nodeA.type?.toLowerCase() ?? '';
    const typeB = nodeB.type?.toLowerCase() ?? '';

    const configResources = ['configmap', 'secret', 'configbundle'];
    const involvesConfigResource = configResources.includes(typeA) || configResources.includes(typeB);

    if (involvesConfigResource) {
      const otherType = configResources.includes(typeA) ? typeB : typeA;
      return ['deployment', 'container', 'volume'].includes(otherType);
    }

    if (typeA !== 'container' && typeB !== 'container') {
      return true;
    }

    const otherType = typeA === 'container' ? typeB : typeA;
    return otherType === 'deployment';
  }

  private getDependencyPriority(type?: string): number {
    if (!type) {
      return 0;
    }
    const normalized = type.toLowerCase();
    return this.dependencyPriority[normalized] ?? 0;
  }

  private orientLinkByDependency(
    startNode: DiagramNode,
    endNode: DiagramNode,
    startPoint?: ConnectionPoint,
    endPoint?: ConnectionPoint
  ): {
    fromNode: DiagramNode;
    toNode: DiagramNode;
    fromPoint?: ConnectionPoint;
    toPoint?: ConnectionPoint;
  } {
    const startType = startNode.type?.toLowerCase();
    const endType = endNode.type?.toLowerCase();
    const configPreferredTypes = new Set(['configmap', 'secret', 'configbundle']);

    const startPreferred = startType ? configPreferredTypes.has(startType) : false;
    const endPreferred = endType ? configPreferredTypes.has(endType) : false;

    if (startPreferred && !endPreferred) {
      return { fromNode: startNode, toNode: endNode, fromPoint: startPoint, toPoint: endPoint };
    }

    if (endPreferred && !startPreferred) {
      return { fromNode: endNode, toNode: startNode, fromPoint: endPoint, toPoint: startPoint };
    }

    const startPriority = this.getDependencyPriority(startNode.type);
    const endPriority = this.getDependencyPriority(endNode.type);

    if (startPriority > endPriority) {
      return { fromNode: startNode, toNode: endNode, fromPoint: startPoint, toPoint: endPoint };
    }

    if (startPriority < endPriority) {
      return { fromNode: endNode, toNode: startNode, fromPoint: endPoint, toPoint: startPoint };
    }

    return { fromNode: startNode, toNode: endNode, fromPoint: startPoint, toPoint: endPoint };
  }

  private resolveIconPath(type?: string): string {
    const normalized = type?.toLowerCase() || this.fallbackIconType;
    return this.iconService.getIconPath(normalized) || this.iconService.getIconPath(this.fallbackIconType) || '';
  }

  isPrimeIcon(icon?: string): boolean {
    return !!icon && icon.trim().startsWith('pi ');
  }

  getPrimeIconClasses(icon?: string): string {
    return icon || '';
  }

  private resolveLinkEndpoint(link: any, endpoint: 'source' | 'target'): string | undefined {
    if (!link) {
      return undefined;
    }
    const altKey = endpoint === 'source' ? 'from' : 'to';
    const nodeIdKey = `${endpoint}NodeId`;
    const legacyKey = endpoint === 'source' ? 'src' : 'dst';
    return link[endpoint] ?? link[nodeIdKey] ?? link[altKey] ?? link[legacyKey];
  }

  private buildDiagramFromClusterData(cluster: Cluster): { nodes: DiagramNode[]; links: DiagramLink[] } {
    const spacingX = 200;
    const spacingY = 160;
    const startX = 160;
    const startY = 160;
    const columns = 4;

    const baseNodes: any[] = Array.isArray(cluster.nodes) ? cluster.nodes : [];
    const diagramNodes: DiagramNode[] = baseNodes.map((node, index) => {
      const hasX = typeof node.x === 'number';
      const hasY = typeof node.y === 'number';

      return this.normalizeDiagramNode(node, {
        x: hasX ? node.x : startX + (index % columns) * spacingX,
        y: hasY ? node.y : startY + Math.floor(index / columns) * spacingY
      });
    });

    const diagramLinks: DiagramLink[] = [];
    const seenLinks = new Set<string>();
    const baseLinks: Link[] = Array.isArray(cluster.links) ? cluster.links : [];

    baseLinks.forEach(link => {
      const sourceId = this.resolveLinkEndpoint(link, 'source');
      const targetId = this.resolveLinkEndpoint(link, 'target');

      if (!sourceId || !targetId) {
        return;
      }

      const sourceNode = diagramNodes.find(node => node.id === sourceId);
      const targetNode = diagramNodes.find(node => node.id === targetId);
      if (!sourceNode || !targetNode) {
        return;
      }

      const oriented = this.orientLinkByDependency(sourceNode, targetNode);
      const fromPoints = this.getConnectionPoints(oriented.fromNode);
      const toPoints = this.getConnectionPoints(oriented.toNode);
      const fromPoint = oriented.fromPoint || fromPoints.find(point => point.side === 'right') || fromPoints[0];
      const toPoint = oriented.toPoint || toPoints.find(point => point.side === 'left') || toPoints[0];

      const key = `${oriented.fromNode.id}->${oriented.toNode.id}`;
      if (seenLinks.has(key)) {
        return;
      }
      seenLinks.add(key);

      diagramLinks.push({
        id: uuidv4(),
        from: oriented.fromNode.id,
        to: oriented.toNode.id,
        fromPoint,
        toPoint
      });
    });

    return { nodes: diagramNodes, links: diagramLinks };
  }

  private rebuildLinksFromClusterLinks(clusterLinks: Link[]): DiagramLink[] {
    const diagramLinks: DiagramLink[] = [];
    const seenLinks = new Set<string>();

    clusterLinks.forEach(link => {
      const sourceId = this.resolveLinkEndpoint(link, 'source');
      const targetId = this.resolveLinkEndpoint(link, 'target');

      if (!sourceId || !targetId) {
        return;
      }

      const sourceNode = this.nodes.find(node => node.id === sourceId);
      const targetNode = this.nodes.find(node => node.id === targetId);
      if (!sourceNode || !targetNode) {
        return;
      }
      const oriented = this.orientLinkByDependency(sourceNode, targetNode);
      const fromPoints = this.getConnectionPoints(oriented.fromNode);
      const toPoints = this.getConnectionPoints(oriented.toNode);
      const fromPoint = oriented.fromPoint || fromPoints.find(point => point.side === 'right') || fromPoints[0];
      const toPoint = oriented.toPoint || toPoints.find(point => point.side === 'left') || toPoints[0];
      const key = `${oriented.fromNode.id}->${oriented.toNode.id}`;
      if (seenLinks.has(key)) {
        return;
      }
      seenLinks.add(key);
      diagramLinks.push({
        id: uuidv4(),
        from: oriented.fromNode.id,
        to: oriented.toNode.id,
        fromPoint,
        toPoint
      });
    });

    return diagramLinks;
  }

  private enforceUmlDependencyOrientationOnLinks(): void {
    this.links = this.links.map(link => {
      const fromNode = this.nodes.find(node => node.id === link.from);
      const toNode = this.nodes.find(node => node.id === link.to);

      if (!fromNode || !toNode) {
        return link;
      }

      const oriented = this.orientLinkByDependency(fromNode, toNode, link.fromPoint, link.toPoint);
      if (oriented.fromNode.id === link.from && oriented.toNode.id === link.to) {
        return link;
      }

      return {
        ...link,
        from: oriented.fromNode.id,
        to: oriented.toNode.id,
        fromPoint: oriented.fromPoint,
        toPoint: oriented.toPoint
      };
    });
  }

  private findClosestConnectionPoint(clientX: number, clientY: number, connectionPoints: ConnectionPoint[]): ConnectionPoint {
    const { x, y } = this.screenToDiagram(clientX, clientY);

    let closestPoint = connectionPoints[0];
    let minDistance = Math.sqrt(Math.pow(x - closestPoint.x, 2) + Math.pow(y - closestPoint.y, 2));

    connectionPoints.forEach(point => {
      const distance = Math.sqrt(Math.pow(x - point.x, 2) + Math.pow(y - point.y, 2));
      if (distance < minDistance) {
        minDistance = distance;
        closestPoint = point;
      }
    });

    return closestPoint;
  }

  private getNodeCenter(node: DiagramNode): { x: number, y: number } {
    const dimensions = this.getNodeOuterSize(node);
    const halfWidth = dimensions.width / 2;
    const halfHeight = dimensions.height / 2;
    return {
      x: node.x + halfWidth,
      y: node.y + halfHeight
    };
  }

  getNodeOuterSize(node: DiagramNode): { width: number; height: number } {
    const contentWidth = node.width ?? this.nodeContentSize;
    const contentHeight = node.height ?? this.nodeContentSize;
    return {
      width: contentWidth + this.nodeBorderWidth * 2,
      height: contentHeight + this.nodeBorderWidth * 2
    };
  }

  getConnectionPoints(node: DiagramNode): ConnectionPoint[] {
    const dimensions = this.getNodeOuterSize(node);
    const halfWidth = dimensions.width / 2;
    const halfHeight = dimensions.height / 2;
    const totalWidth = dimensions.width;
    const totalHeight = dimensions.height;
    return [
      { side: 'top', x: node.x + halfWidth, y: node.y },
      { side: 'right', x: node.x + totalWidth, y: node.y + halfHeight },
      { side: 'bottom', x: node.x + halfWidth, y: node.y + totalHeight },
      { side: 'left', x: node.x, y: node.y + halfHeight }
    ];
  }

  // Connection dragging is now handled through Interact.js on connection-point elements

  private findNodeByElement(nodeElement: HTMLElement): DiagramNode | null {
    const nodeId = nodeElement.getAttribute('data-node-id');
    if (!nodeId) {
      return null;
    }
    return this.nodes.find(node => node.id === nodeId) || null;
  }

  private highlightNearbyConnectionPoints(clientX: number, clientY: number) {
    const threshold = this.connectionCaptureRadius;
    
    this.nodes.forEach(node => {
      const connectionPoints = this.getConnectionPoints(node);
      connectionPoints.forEach(point => {
        const pointScreen = this.diagramToScreen(point.x, point.y);
        const pointScreenX = pointScreen.x;
        const pointScreenY = pointScreen.y;
        
        const distance = Math.sqrt(
          Math.pow(clientX - pointScreenX, 2) + 
          Math.pow(clientY - pointScreenY, 2)
        );
        
        // Add visual feedback for nearby connection points
        const pointElement = this.getConnectionPointElement(node, point);
        if (pointElement) {
          if (distance <= threshold) {
            pointElement.classList.add('connection-point-highlight');
          } else {
            pointElement.classList.remove('connection-point-highlight');
          }
        }
      });
    });
  }

  private getConnectionPointElement(node: DiagramNode, point: ConnectionPoint): HTMLElement | null {
    const nodeElement = document.querySelector(`.diagram-node[data-node-id="${node.id}"]`);
    if (!nodeElement) return null;
    
    const connectionPoints = nodeElement.querySelectorAll('.connection-point');
    const sides = ['top', 'right', 'bottom', 'left'];
    const sideIndex = sides.indexOf(point.side);
    
    return connectionPoints[sideIndex] as HTMLElement || null;
  }

  private findNearestDroppablePoint(
    clientX: number,
    clientY: number,
    excludeNodeId?: string
  ): { node: DiagramNode; point: ConnectionPoint } | null {
    let closest: { node: DiagramNode; point: ConnectionPoint } | null = null;
    let bestDistance = this.connectionCaptureRadius;

    this.nodes.forEach(node => {
      if (node.id === excludeNodeId) {
        return;
      }

      const connectionPoints = this.getConnectionPoints(node);
      connectionPoints.forEach(point => {
        const screenPoint = this.diagramToScreen(point.x, point.y);
        const distance = Math.hypot(clientX - screenPoint.x, clientY - screenPoint.y);

        if (distance <= bestDistance) {
          bestDistance = distance;
          closest = { node, point };
        }
      });
    });

    return closest;
  }

  private resolveDynamicAnchors(
    link: DiagramLink,
    fromNode: DiagramNode,
    toNode: DiagramNode
  ): { fromPoint: ConnectionPoint; toPoint: ConnectionPoint } {
    const fromPoint = this.getNearestConnectionPointTowardsTarget(fromNode, toNode);
    const toPoint = this.getNearestConnectionPointTowardsTarget(toNode, fromNode);
    link.fromPoint = fromPoint;
    link.toPoint = toPoint;
    return { fromPoint, toPoint };
  }

  private getNearestConnectionPointTowardsTarget(node: DiagramNode, targetNode: DiagramNode): ConnectionPoint {
    const targetCenter = this.getNodeCenter(targetNode);
    const points = this.getConnectionPoints(node);
    let closest = points[0];
    let minDistance = Number.POSITIVE_INFINITY;
    points.forEach(point => {
      const distance = Math.hypot(point.x - targetCenter.x, point.y - targetCenter.y);
      if (distance < minDistance) {
        minDistance = distance;
        closest = point;
      }
    });
    return closest;
  }

  private createTempLine(x1: number, y1: number, x2: number, y2: number) {
    this.tempLine = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    this.tempLine.setAttribute('x1', x1.toString());
    this.tempLine.setAttribute('y1', y1.toString());
    this.tempLine.setAttribute('x2', x2.toString());
    this.tempLine.setAttribute('y2', y2.toString());
    this.tempLine.setAttribute('stroke', '#28a745');
    this.tempLine.setAttribute('stroke-width', '3');
    this.tempLine.setAttribute('stroke-dasharray', '5,5');
    this.tempLine.style.pointerEvents = 'none';
    
    this.svgElement.appendChild(this.tempLine);
  }

  private cancelConnection() {
    this.isConnecting = false;
    this.isDraggingConnection = false;
    this.connectionStartNode = null;
    this.connectionStartPoint = null;
    
    // Remove temporary line
    if (this.tempLine) {
      this.tempLine.remove();
      this.tempLine = null;
    }
    
    // Remove all connection point highlights
    this.clearConnectionPointHighlights();
  }

  private clearConnectionPointHighlights() {
    const highlightedPoints = document.querySelectorAll('.connection-point-highlight');
    highlightedPoints.forEach(point => {
      point.classList.remove('connection-point-highlight');
    });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    // Only cancel connection if not clicking on a connection point
    const target = event.target as HTMLElement;
    const isConnectionPoint = target.classList.contains('connection-point');
    
    if (this.isConnecting && !isConnectionPoint) {
      this.cancelConnection();
    }
    if (this.isDraggingConnection) {
      this.cancelConnection();
    }
  }

  private saveToUndoStack() {
    // Deep clone current state
    const currentState = {
      nodes: JSON.parse(JSON.stringify(this.nodes.map(n => ({ 
        id: n.id, name: n.name, type: n.type, icon: n.icon, x: n.x, y: n.y, width: n.width, height: n.height, role: n.role 
      })))),
      links: JSON.parse(JSON.stringify(this.links.map(l => ({ 
        id: l.id, from: l.from, to: l.to, fromPoint: l.fromPoint, toPoint: l.toPoint 
      })))),
      rawManifests: JSON.parse(JSON.stringify(this.rawManifests))
    };

    this.undoStack.push(currentState);

    // Limit undo stack size
    if (this.undoStack.length > this.maxUndoSteps) {
      this.undoStack.shift();
    }
  }

  private undo() {
    if (this.undoStack.length === 0) return;

    const previousState = this.undoStack.pop()!;
    
    // Clear current visual elements
    this.clearDiagram();
    
    // Restore previous state
    this.nodes = previousState.nodes;
    this.links = previousState.links;
    this.rawManifests = previousState.rawManifests || [];
    
    // Clear selections
    this.clearSelection();

    // Re-render diagram
    this.renderLinks();
    this.updateDiagramData();
  }

  private clearDiagram() {
    // Remove all SVG elements
    this.links.forEach(link => {
      if (link.element) {
        link.element.remove();
      }
    });
    
    // Clear the SVG container
    while (this.svgElement.firstChild) {
      this.svgElement.removeChild(this.svgElement.firstChild);
    }
  }

  private updateDiagramData() {
    const validNames = new Set(this.nodes.map(n => n.name));
    this.rawManifests = (this.rawManifests || []).filter((entry: any) => {
      if (!entry || typeof entry !== 'object') {
        return false;
      }
      const name = typeof entry.name === 'string' ? entry.name : undefined;
      return !!name && validNames.has(name);
    });

    const serializedLinks = this.links.map(l => ({
      id: l.id,
      from: l.from,
      to: l.to,
      fromPoint: l.fromPoint,
      toPoint: l.toPoint
    }));

    const clusterLinks = this.links.map(l => new Link(l.from, l.to));

    const diagramData = JSON.stringify({
      nodes: this.nodes.map(n => ({
        id: n.id,
        name: n.name,
        type: n.type,
        icon: n.icon,
        x: n.x,
        y: n.y,
        width: n.width ?? this.nodeContentSize,
        height: n.height ?? this.nodeContentSize,
        isAffected: !!n.isAffected,
        ...(n.type === 'container' && n.role ? { role: n.role } : {}),
        ...(n.type === 'deployment' && n.workloadType ? { workloadType: n.workloadType } : {})
      })),
      links: serializedLinks,
      rawManifests: this.rawManifests
    });

    this.store.dispatch(actions.updateDiagram({ diagramData, links: clusterLinks }));
    this.updateSurfaceSize();
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    interact('.diagram-node').unset();
    interact('.connection-point').unset();
    const viewportEl = this.diagramCanvas?.nativeElement as HTMLElement | undefined;
    if (viewportEl) {
      viewportEl.removeEventListener('pointermove', this.panPointerMove as any);
      viewportEl.removeEventListener('pointerup', this.panPointerUp as any);
      viewportEl.removeEventListener('pointercancel', this.panPointerUp as any);
    }
  }

  private updateSurfaceSize(): void {
    const minWidth = this.diagramCanvas?.nativeElement?.clientWidth || this.surfaceWidth;
    const minHeight = this.diagramCanvas?.nativeElement?.clientHeight || this.surfaceHeight;

    let maxX = 0;
    let maxY = 0;

    this.nodes.forEach(node => {
      const size = this.getNodeOuterSize(node);
      maxX = Math.max(maxX, node.x + size.width);
      maxY = Math.max(maxY, node.y + size.height);
    });

    this.surfaceWidth = Math.max(minWidth, maxX + this.surfacePadding);
    this.surfaceHeight = Math.max(minHeight, maxY + this.surfacePadding);

    if (this.svgElement) {
      this.svgElement.style.width = `${this.surfaceWidth}px`;
      this.svgElement.style.height = `${this.surfaceHeight}px`;
    }

    this.updateViewportRect();
    this.applyViewportTransform();
  }

  private updateViewportRect(): void {
    const canvas = this.diagramCanvas?.nativeElement;
    if (!canvas) {
      return;
    }
    const scale = this.viewportState.scale || 1;
    this.viewportRect = {
      x: -this.viewportState.offsetX / scale,
      y: -this.viewportState.offsetY / scale,
      width: canvas.clientWidth / scale,
      height: canvas.clientHeight / scale
    };
    this.updateGridBackground();
  }

  toggleMinimap(): void {
    this.minimapVisible = !this.minimapVisible;
    this.persistMinimapPreference();
  }

  onMinimapClick(event: MouseEvent): void {
    event.stopPropagation();
    this.panDiagramToMinimapEvent(event);
  }

  onMinimapMouseDown(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDraggingMinimap = true;
    this.panDiagramToMinimapEvent(event);
  }

  @HostListener('document:mousemove', ['$event'])
  onDocumentMouseMove(event: MouseEvent): void {
    if (this.isDraggingMinimap) {
      this.panDiagramToMinimapEvent(event);
    }
  }

  @HostListener('document:mouseup')
  onDocumentMouseUp(): void {
    this.isDraggingMinimap = false;
  }

  private panDiagramToMinimapEvent(event: MouseEvent): void {
    const svgEl = this.minimapSvg?.nativeElement;
    if (!svgEl) {
      return;
    }
    const rect = svgEl.getBoundingClientRect();
    const { scale, offsetX, offsetY } = this.getMinimapScale(rect);
    if (scale <= 0) {
      return;
    }

    const canvas = this.diagramCanvas.nativeElement;
    const targetX = (event.clientX - rect.left - offsetX) / scale;
    const targetY = (event.clientY - rect.top - offsetY) / scale;

    const viewportWidth = canvas.clientWidth;
    const viewportHeight = canvas.clientHeight;

    const newOffsetX = viewportWidth / 2 - targetX * this.viewportState.scale;
    const newOffsetY = viewportHeight / 2 - targetY * this.viewportState.scale;

    this.viewportState.offsetX = newOffsetX;
    this.viewportState.offsetY = newOffsetY;
    this.applyViewportTransform();
    this.updateViewportRect();
  }

  private getMinimapScale(rect: DOMRect): { scale: number; offsetX: number; offsetY: number } {
    const scaleX = rect.width / this.surfaceWidth;
    const scaleY = rect.height / this.surfaceHeight;
    const scale = Math.min(scaleX, scaleY);
    const offsetX = (rect.width - this.surfaceWidth * scale) / 2;
    const offsetY = (rect.height - this.surfaceHeight * scale) / 2;
    return { scale, offsetX, offsetY };
  }

  getMinimapLinkEndpoints(link: DiagramLink): { x1: number; y1: number; x2: number; y2: number } | null {
    const fromNode = this.nodes.find(n => n.id === link.from);
    const toNode = this.nodes.find(n => n.id === link.to);
    if (!fromNode || !toNode) {
      return null;
    }
    const fromCenter = this.getNodeCenter(fromNode);
    const toCenter = this.getNodeCenter(toNode);
    return { x1: fromCenter.x, y1: fromCenter.y, x2: toCenter.x, y2: toCenter.y };
  }

  private persistMinimapPreference(): void {
    try {
      localStorage.setItem(this.minimapPreferenceKey, this.minimapVisible ? '1' : '0');
    } catch {
      // Ignore storage errors
    }
  }

  private restoreMinimapPreference(): boolean {
    try {
      const stored = localStorage.getItem(this.minimapPreferenceKey);
      if (stored === null) {
        return true;
      }
      return stored === '1';
    } catch {
      return true;
    }
  }

  private applyViewportTransform(): void {
    const { offsetX, offsetY, scale } = this.viewportState;
    this.viewportTransform = `translate(${offsetX}px, ${offsetY}px) scale(${scale})`;
    if (this.diagramSurface?.nativeElement) {
      (this.diagramSurface.nativeElement as HTMLElement).style.transform = this.viewportTransform;
    }
    this.updateGridBackground();
  }

  private relativeToDiagram(x: number, y: number): { x: number; y: number } {
    const scale = this.viewportState.scale || 1;
    return {
      x: (x - this.viewportState.offsetX) / scale,
      y: (y - this.viewportState.offsetY) / scale
    };
  }

  private screenToDiagram(clientX: number, clientY: number): { x: number; y: number } {
    const rect = this.diagramCanvas.nativeElement.getBoundingClientRect();
    const x = (clientX - rect.left - this.viewportState.offsetX) / this.viewportState.scale;
    const y = (clientY - rect.top - this.viewportState.offsetY) / this.viewportState.scale;
    return { x, y };
  }

  private diagramToScreen(x: number, y: number): { x: number; y: number } {
    const rect = this.diagramCanvas.nativeElement.getBoundingClientRect();
    return {
      x: rect.left + this.viewportState.offsetX + x * this.viewportState.scale,
      y: rect.top + this.viewportState.offsetY + y * this.viewportState.scale
    };
  }

  private updateGridBackground(): void {
    if (!this.diagramGrid) {
      return;
    }
    const gridSize = 20;
    const scale = this.viewportState.scale || 1;
    const size = gridSize * scale;
    const offsetX = this.viewportState.offsetX % size;
    const offsetY = this.viewportState.offsetY % size;
    const gridEl = this.diagramGrid.nativeElement;
    gridEl.style.backgroundSize = `${size}px ${size}px`;
    gridEl.style.backgroundPosition = `${offsetX}px ${offsetY}px`;
  }

  private shouldStartPan(event: PointerEvent): boolean {
    const target = event.target as HTMLElement | null;
    if (this.isConnecting || this.isDraggingConnection) {
      return false;
    }
    if (this.handMode) {
      return true;
    }
    if (!target) {
      return false;
    }
    const isNode = !!target.closest('.diagram-node');
    const isLink = !!target.closest('.diagram-link');
    const isMinimap = !!target.closest('.diagram-minimap');
    const isToolbar = !!target.closest('.palette-actions');
    return !isNode && !isLink && !isMinimap && !isToolbar;
  }

  private setPanActive(active: boolean): void {
    this.isPanning = active;
    this.updatePanCursor();
  }

  private updatePanCursor(): void {
    const viewportEl = this.diagramCanvas?.nativeElement as HTMLElement | undefined;
    if (!viewportEl) {
      return;
    }
    const shouldShowHand = this.handMode || this.isPanning;
    viewportEl.classList.toggle('pan-enabled', shouldShowHand);
    viewportEl.classList.toggle('pan-active', this.isPanning);
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (event.code === 'Space' && !event.repeat && !this.isEditingFormField(event.target)) {
      this.handMode = true;
      this.toggleNodeInteractions(false);
      this.updatePanCursor();
      event.preventDefault();
    }
  }

  @HostListener('window:keyup', ['$event'])
  onKeyUp(event: KeyboardEvent): void {
    if (event.code === 'Space' && !this.isEditingFormField(event.target)) {
      this.handMode = false;
      this.toggleNodeInteractions(true);
      this.updatePanCursor();
      event.preventDefault();
    }
  }

  private toggleNodeInteractions(enabled: boolean): void {
    interact('.diagram-node').draggable({ enabled });
  }
}
