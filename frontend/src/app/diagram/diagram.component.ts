import { DiagramService } from './../services/diagram.service';
import { IconService } from './../services/icon.service';
import { AfterViewInit, Component, ElementRef, HostListener, NgZone, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Store, select } from '@ngrx/store';
import { v4 as uuidv4 } from 'uuid';
import { Subscription, debounceTime, filter, finalize, interval, of, switchMap, take, tap, catchError } from 'rxjs';
import * as actions from '../store/actions/actions';
import { getCurrentCluster, getNodeById, selectClusterDiagram } from '../store/selectors/selectors';
import { Cluster, ClusterExportMode } from '../model/cluster.class';
import { DragDropData, DropEvent } from '../directives/drag-drop.directive';
import { AiAssistantService, AiChatMessage, AiImportYamlResponse, AiExportYamlResponse, AiHelmChartExportResponse } from '../services/ai-assistant.service';
import { NotificationService } from '../services/notification.service';
import { Link, LinkType } from '../model/link.class';
import { ContainerRole, toContainerRole } from '../model/container.class';
import { ClusterStatusEnum } from '../cluster/enum/cluster.-status-enum';
import { PodShellService } from '../services/pod-shell.service';
import { KubeExplorerService } from '../services/kube-explorer.service';
import { PodSummary, WorkloadHealth } from '../model/kube-summary';
import { OverlayPanel } from 'primeng/overlaypanel';
import { ConfigurationChangeService } from '../services/configuration-change.service';
import { ResourceSyncService } from '../services/resource-sync.service';
import { ConfigBundleMeta } from '../model/config-bundle.model';
import { LinkUpdateService } from '../services/link-update.service';
import { ClusterService } from '../services/cluster.service';
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
  command?: string[];
  args?: string[];
  isAffected?: boolean;
  forwardActive?: boolean;
  element?: HTMLElement;
  bundleMeta?: ConfigBundleMeta;
  hasHealthIssue?: boolean;
  healthReason?: string;
  replicas?: number;
  rbacNodeType?: 'ROLE' | 'ROLEBINDING';
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
  type: LinkType;
  note?: string;
  containerRole?: ContainerRole;
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
  connectionHelpText = $localize`:@@diagram.connectionHelpText:Drag to connect this block to its UML dependency`;
  clusterYamlDialogVisible = false;
  clusterYamlMode: 'import' | 'export' = 'import';
  clusterYamlText = '';
  clusterYamlError: string | null = null;
  clusterYamlLoading = false;
  clusterYamlFileName = '';
  clusterYamlImportMode: 'replace' | 'append' = 'replace';
  clusterYamlImportModeOptions = [
    { label: $localize`:@@diagram.overwriteDiagram:Overwrite diagram`, value: 'replace' as const },
    { label: $localize`:@@diagram.addToDiagramOption:Add to diagram`, value: 'append' as const }
  ];
  clusterExportMode: ClusterExportMode = 'FLAT_YAML';
  clusterExportModeOptions = [
    { label: $localize`:@@diagram.flatYaml:Flat YAML`, value: 'FLAT_YAML' as ClusterExportMode },
    { label: $localize`:@@diagram.helmChartZip:Helm Chart (.zip)`, value: 'HELM_CHART' as ClusterExportMode }
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
    serviceaccount: 7,
    service: 6,
    ingress: 5,
    istio: 5,
    deployment: 4,
    cr: 3,
    job: 4,
    container: 2,
    volume: 1,
    configmap: 1,
    configbundle: 1,
    secret: 1
  };
  private readonly fallbackIconType = 'container';
  private readonly manifestKindsWithoutNodes = new Set(['virtualservice', 'gateway', 'istio']);
  private readonly hiddenNodeTypes = new Set(['istio', 'virtualservice', 'gateway']);
  private readonly connectionCaptureRadius = 28;
  podMenuPods: PodSummary[] = [];
  podMenuLoading = false;
  podMenuError: string | null = null;
  private podMenuContext: { namespace: string; deploymentName: string } | null = null;
  podShellDialogVisible = false;
  activeShellTarget: { namespace: string; podName: string; containerName?: string } | null = null;
  private workloadHealthMap = new Map<string, WorkloadHealth>();
  private testHarnessRegistered = false;

  get minimapToggleLabel(): string {
    return this.minimapVisible
      ? $localize`:@@diagram.hideMap:Hide map`
      : $localize`:@@diagram.showMap:Show map`;
  }

  constructor(
    private iconService: IconService,
    private store: Store,
    private diagramService: DiagramService,
    private linkUpdateService: LinkUpdateService,
    private aiAssistantService: AiAssistantService,
    private notificationService: NotificationService,
    private podShellService: PodShellService,
    private kubeExplorerService: KubeExplorerService,
    private configurationChangeService: ConfigurationChangeService,
    public resourceSyncService: ResourceSyncService,
    private clusterService: ClusterService,
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
          this.syncLinkMetadataFromCluster();
          this.refreshWorkloadHealth();
        })
      ).subscribe()
    );

    this.subscription.add(
      this.linkUpdateService.redraw$.subscribe(({ linkId, changes }) => {
        this.zone.run(() => this.applyLinkUpdate(linkId, changes));
      })
    );

    this.subscription.add(
      interval(15000).pipe(
        switchMap(() => {
          if (!this.isNamespaceDeployed() || !this.currentClusterSnapshot?.nameSpace) {
            return of<WorkloadHealth[]>([]);
          }
          return this.kubeExplorerService.getWorkloadHealth(this.currentClusterSnapshot.nameSpace).pipe(
            catchError(() => of<WorkloadHealth[]>([]))
          );
        })
      ).subscribe(health => {
        this.applyWorkloadHealth(health);
      })
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
    this.registerTestHarness();
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
    const isAccessPolicy = (event.data.type || '').toLowerCase() === 'accesspolicy';
    const isRoleBinding = isAccessPolicy && (baseName || '').toLowerCase().includes('role-binding');
    this.createNode(event.data.type, baseName, event.data.icon, coords.x, coords.y, {
      initialNodePatch: isAccessPolicy
        ? {
            rbacNodeType: isRoleBinding ? 'ROLEBINDING' : 'ROLE',
            ...(isRoleBinding ? { bindingKind: 'RoleBinding' } : { roleKind: 'Role' })
          }
        : undefined
    });
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
      this.notificationService.warn(
        $localize`:@@diagram.addInstructionTitle:Add an instruction`,
        $localize`:@@diagram.addInstructionDetail:Describe the blocks you want the assistant to create.`
      );
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
        const detail = error?.error || error?.message || $localize`:@@diagram.localAiRequestFailed:Local AI request failed.`;
        this.aiError = typeof detail === 'string' ? detail : $localize`:@@diagram.localAiRequestFailed:Local AI request failed.`;
        this.notificationService.error($localize`:@@diagram.aiRequestFailedTitle:AI request failed`, this.aiError || undefined);
        this.aiLoading = false;
      }
    });
  }

  applyAiSuggestions(): void {
    if (!this.aiSuggestions.length) {
      this.notificationService.warn(
        $localize`:@@diagram.nothingToAddTitle:Nothing to add`,
        $localize`:@@diagram.nothingToAddDetail:Ask the assistant to produce blocks before applying.`
      );
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
        this.notificationService.warn(
          $localize`:@@diagram.unsupportedBlockTypeTitle:Unsupported block type`,
          $localize`:@@diagram.skippingUnsupportedBlock:Skipping ${suggestion.name} (${normalizedType}).`
        );
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
          {
            skipUndo: true,
            deferUpdate: true,
            type: link?.type === 'Use' ? 'Use' : 'Expose'
          }
        );
      });
    });

    this.updateDiagramData();
    this.notificationService.success(
      $localize`:@@diagram.updatedTitle:Diagram updated`,
      $localize`:@@diagram.updatedByAiDetail:AI generated blocks were added to the canvas.`
    );
    this.aiDialogVisible = false;
    this.aiSuggestions = [];
    this.aiPrompt = '';
  }

  openClusterYamlDialog(mode: 'import' | 'export'): void {
    if (mode === 'export' && !this.isExportAllowed()) {
      this.notificationService.warn(
        $localize`:@@diagram.templateRequiredTitle:Template required`,
        $localize`:@@diagram.templateRequiredDetail:Generate the template before exporting YAML.`
      );
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
    return this.currentClusterSnapshot?.status === ClusterStatusEnum.READY_FOR_DEPLOYMENT
      || this.currentClusterSnapshot?.status === ClusterStatusEnum.DEPLOYED;
  }

  private fetchClusterExport(): void {
    this.clusterYamlLoading = true;
    this.clusterYamlError = null;

    this.store.select(getCurrentCluster).pipe(take(1)).subscribe(cluster => {
      if (!cluster) {
        this.notificationService.warn(
          $localize`:@@diagram.noDiagramToExportTitle:No diagram to export`,
          $localize`:@@diagram.noDiagramToExportDetail:Create or load a diagram before exporting YAML.`
        );
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
    const detail = error?.error || error?.message || $localize`:@@diagram.exportFailedDetail:Diagram export failed.`;
    this.notificationService.error($localize`:@@diagram.exportFailedTitle:Export failed`, typeof detail === 'string' ? detail : undefined);
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
      this.notificationService.warn(
        $localize`:@@diagram.addYamlTitle:Add YAML`,
        $localize`:@@diagram.addYamlDetail:Paste diagram YAML before importing.`
      );
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
        this.applyImportedCluster(response, this.clusterYamlImportMode);
        this.persistImportedCluster();
        const message = this.clusterYamlImportMode === 'append'
          ? $localize`:@@diagram.importedAppendDetail:Diagram updated with the imported YAML.`
          : $localize`:@@diagram.importedReplaceDetail:Diagram updated from YAML.`;
        this.notificationService.success($localize`:@@diagram.importedTitle:Diagram imported`, message);
        this.clusterYamlDialogVisible = false;
      },
      error: (error) => {
        const detail = error?.error || error?.message || $localize`:@@diagram.importFailedDetail:Diagram import failed.`;
        this.clusterYamlError = typeof detail === 'string' ? detail : $localize`:@@diagram.importFailedDetail:Diagram import failed.`;
      }
    });
  }

  copyExportedYaml(): void {
    if (!this.clusterYamlText) {
      return;
    }
    if (navigator && navigator.clipboard) {
      navigator.clipboard.writeText(this.clusterYamlText).then(
        () => this.notificationService.success(
          $localize`:@@common.copied:Copied`,
          $localize`:@@diagram.yamlCopiedDetail:Namespace YAML copied to clipboard.`
        ),
        () => this.notificationService.error(
          $localize`:@@diagram.copyFailedTitle:Copy failed`,
          $localize`:@@diagram.copyFailedDetail:Unable to copy YAML to clipboard.`
        )
      );
    } else {
      this.notificationService.warn(
        $localize`:@@diagram.clipboardUnavailableTitle:Clipboard unavailable`,
        $localize`:@@diagram.clipboardUnavailableDetail:Copy not supported in this environment.`
      );
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
        content: $localize`:@@diagram.chatGreeting:How can I help with your Kubernetes architecture today?`,
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
            content: $localize`:@@diagram.chatNoReply:I could not generate a reply. Please try again.`,
            timestamp: new Date(),
            error: true
          });
        }
        this.chatLoading = false;
      },
      error: error => {
        const detail = error?.error || error?.message || $localize`:@@diagram.chatRequestFailedDetail:Local AI chat request failed.`;
        this.chatMessages.push({
          role: 'assistant',
          content: typeof detail === 'string' ? detail : $localize`:@@diagram.chatRequestFailedDetail:Local AI chat request failed.`,
          timestamp: new Date(),
          error: true
        });
        this.notificationService.error($localize`:@@diagram.chatFailedTitle:Chat failed`, typeof detail === 'string' ? detail : undefined);
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
      this.notificationService.warn(
        $localize`:@@diagram.noYamlFoundTitle:No YAML found`,
        $localize`:@@diagram.noYamlFoundDetail:Ask the assistant to provide YAML before importing.`
      );
      return;
    }

    this.importingFromChat = true;
    this.aiAssistantService.importYaml({ yaml, name: 'AI Generated Diagram' }).subscribe({
      next: (response) => {
        this.applyImportedCluster(response, this.clusterYamlImportMode);
        this.persistImportedCluster();
        const message = this.clusterYamlImportMode === 'append'
          ? $localize`:@@diagram.importedAppendDetail:Diagram updated with the imported YAML.`
          : $localize`:@@diagram.importedReplaceDetail:Diagram updated from YAML.`;
        this.notificationService.success($localize`:@@diagram.importedTitle:Diagram imported`, message);
        this.importingFromChat = false;
      },
      error: (error) => {
        const detail = error?.error || error?.message || $localize`:@@diagram.yamlImportFailedDetail:YAML import failed.`;
        this.notificationService.error($localize`:@@diagram.importFailedTitle:Import failed`, typeof detail === 'string' ? detail : undefined);
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

  private applyImportedCluster(imported: AiImportYamlResponse, mode: 'replace' | 'append' = 'replace'): void {
    const cluster = Cluster.fromJSON(imported);
    const current = this.currentClusterSnapshot;
    if (!cluster.id && current?.id) {
      cluster.id = current.id;
      cluster.name = current.name || cluster.name;
      cluster.nameSpace = current.nameSpace || cluster.nameSpace;
      cluster.status = current.status || cluster.status;
    }

    if (mode === 'append' && this.nodes.length) {
      this.appendImportedCluster(cluster);
      this.diagramService.clearSelectedNode();
      return;
    }

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
      this.enforceLinkOrientation();
      this.rawManifests = [];
    }

    this.syncConfigBundleMetaFromCluster();
    this.renderLinks();
    this.updateLinkStyles();
    this.diagramService.clearSelectedNode();
  }

  private appendImportedCluster(cluster: Cluster): void {
    const snapshot = this.buildImportedSnapshot(cluster);
    if (!snapshot.nodes.length) {
      return;
    }

    const offset = this.computeAppendOffset(this.nodes, snapshot.nodes);
    const existingIds = new Set(this.nodes.map(node => node.id));
    const idMap = new Map<string, string>();

    const mappedNodes = snapshot.nodes.map(node => {
      const originalId = node.id;
      let nextId = originalId;
      if (existingIds.has(nextId) || idMap.has(nextId)) {
        nextId = uuidv4();
      }
      idMap.set(originalId, nextId);
      return {
        ...node,
        id: nextId,
        x: node.x + offset.x,
        y: node.y + offset.y
      };
    });

    const existingLinkKeys = new Set(this.links.map(link => `${link.from}->${link.to}`));
    const mappedLinks = snapshot.links
      .map(link => {
        const from = idMap.get(link.from) ?? link.from;
        const to = idMap.get(link.to) ?? link.to;
        if (!from || !to) {
          return null;
        }
        const key = `${from}->${to}`;
        if (existingLinkKeys.has(key)) {
          return null;
        }
        existingLinkKeys.add(key);
        return {
          ...link,
          id: uuidv4(),
          from,
          to
        };
      })
      .filter((link): link is DiagramLink => !!link);

    this.nodes = [...this.nodes, ...mappedNodes];
    this.links = [...this.links, ...mappedLinks];
    if (snapshot.rawManifests.length) {
      this.rawManifests = [...this.rawManifests, ...snapshot.rawManifests];
    }

    this.appendClusterSnapshot(cluster, idMap);
    this.enforceLinkOrientation();
    this.renderLinks();
    this.updateLinkStyles();
    this.updateDiagramData();
  }

  private buildImportedSnapshot(cluster: Cluster): { nodes: DiagramNode[]; links: DiagramLink[]; rawManifests: any[] } {
    if (cluster.diagram) {
      return this.parseDiagramData(cluster.diagram);
    }
    const snapshot = this.buildDiagramFromClusterData(cluster);
    return { nodes: snapshot.nodes, links: snapshot.links, rawManifests: [] };
  }

  private parseDiagramData(diagramData: string): { nodes: DiagramNode[]; links: DiagramLink[]; rawManifests: any[] } {
    try {
      const data = JSON.parse(diagramData);
      const parsedNodes = Array.isArray(data.nodes) ? data.nodes : [];
      const nodes: DiagramNode[] = parsedNodes
        .filter((node: any) => {
          const type = ((node?.type || node?.kind || '') as string).toLowerCase();
          return type !== 'pod' && !this.hiddenNodeTypes.has(type);
        })
        .map((node: any) => this.normalizeDiagramNode(node));

      const parsedLinks = Array.isArray(data.links) ? data.links : [];
      const links = parsedLinks
        .filter((link: any) => link.from && link.to &&
          nodes.some((node: DiagramNode) => node.id === link.from) &&
          nodes.some((node: DiagramNode) => node.id === link.to))
        .map((link: any) => ({
          ...link,
          id: link?.id || uuidv4(),
          type: this.resolveLinkType(link?.type),
          note: typeof link?.note === 'string' ? link.note : undefined,
          containerRole: toContainerRole(link?.containerRole) || undefined
        })) as DiagramLink[];

      return {
        nodes,
        links,
        rawManifests: Array.isArray(data.rawManifests) ? data.rawManifests : []
      };
    } catch (error) {
      console.error('Error parsing diagram data:', error);
      return { nodes: [], links: [], rawManifests: [] };
    }
  }

  private computeAppendOffset(existing: DiagramNode[], incoming: DiagramNode[]): { x: number; y: number } {
    if (!existing.length || !incoming.length) {
      return { x: 0, y: 0 };
    }
    const maxX = Math.max(...existing.map(node => node.x));
    const minIncomingX = Math.min(...incoming.map(node => node.x));
    const padding = 200;
    return { x: maxX + padding - minIncomingX, y: 0 };
  }

  private appendClusterSnapshot(cluster: Cluster, idMap: Map<string, string>): void {
    if (!this.currentClusterSnapshot) {
      this.currentClusterSnapshot = new Cluster();
    }

    const existingNodes = Array.isArray(this.currentClusterSnapshot.nodes) ? this.currentClusterSnapshot.nodes : [];
    const existingLinks = Array.isArray(this.currentClusterSnapshot.links) ? this.currentClusterSnapshot.links : [];

    const mappedNodes = (cluster.nodes || []).map((node: any) => ({
      ...node,
      id: idMap.get(node.id) ?? node.id
    }));

    const mappedLinks = (cluster.links || [])
      .map((link: any) => {
        const source = idMap.get(link.source ?? link.from ?? link.src ?? link.sourceId) ?? link.source ?? link.from ?? link.src ?? link.sourceId;
        const target = idMap.get(link.target ?? link.to ?? link.dst ?? link.targetId) ?? link.target ?? link.to ?? link.dst ?? link.targetId;
        if (!source || !target) {
          return null;
        }
        return new Link({
          id: link.id || uuidv4(),
          source: String(source),
          target: String(target),
          type: link.type,
          note: link.note,
          containerRole: link.containerRole
        });
      })
      .filter((link: Link | null): link is Link => !!link);

    const mergedNodes = [...existingNodes, ...mappedNodes];
    const mergedLinks = [...existingLinks, ...mappedLinks];

    const base = this.currentClusterSnapshot ?? new Cluster();
    const updatedCluster = new Cluster(
      base.id,
      base.name,
      mergedNodes,
      mergedLinks,
      base.diagram,
      base.nameSpace,
      base.status,
      base.exportMode
    );
    this.store.dispatch(actions.updateCluster({ cluster: updatedCluster }));
  }

  onYamlFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) {
      return;
    }

    const files = Array.from(input.files);
    this.clusterYamlFileName = files.length === 1
      ? files[0].name
      : `${files.length} files`;

    const readFile = (file: File): Promise<string> => new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const result = reader.result;
        resolve(typeof result === 'string' ? result : new TextDecoder().decode(result as ArrayBuffer));
      };
      reader.onerror = () => reject(new Error($localize`:@@diagram.yamlFileReadFailed:Unable to read the selected YAML file.`));
      reader.readAsText(file);
    });

    Promise.all(files.map(readFile))
      .then(contents => {
        const joined = contents
          .map(text => text.trim())
          .filter(text => text.length > 0)
          .join('\n---\n');
        this.clusterYamlText = joined;
      })
      .catch(() => {
        this.notificationService.error(
          $localize`:@@diagram.fileReadFailedTitle:File read failed`,
          $localize`:@@diagram.yamlFileReadFailed:Unable to read the selected YAML file.`
        );
      })
      .finally(() => {
        input.value = '';
      });
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
        throw new Error($localize`:@@diagram.aiResponseNodesMissing:Response does not include a nodes array.`);
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
        this.aiError = $localize`:@@diagram.aiNoValidNodes:The assistant did not return any valid nodes.`;
        return;
      }

      this.aiSuggestions = suggestions;
      this.aiError = null;
    } catch (error: any) {
      this.aiSuggestions = [];
      this.aiError = $localize`:@@diagram.aiParseFailed:Failed to parse AI response. Ensure the local model returns valid JSON.`;
      this.notificationService.error($localize`:@@diagram.invalidAiResponseTitle:Invalid AI response`, this.aiError);
    }
  }

  private createNode(
    type: string,
    baseName: string,
    icon: string,
    x: number,
    y: number,
    options?: {
      preferredName?: string;
      skipUndo?: boolean;
      deferUpdate?: boolean;
      initialNodePatch?: Record<string, any>;
    }
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
      ...(normalizedType === 'deployment' ? { workloadType: 'DEPLOYMENT' as DiagramNode['workloadType'] } : {}),
      ...((normalizedType === 'accesspolicy' && options?.initialNodePatch?.['rbacNodeType'])
        ? { rbacNodeType: options.initialNodePatch['rbacNodeType'] === 'ROLEBINDING' ? 'ROLEBINDING' : 'ROLE' }
        : {})
    };

    this.nodes.push(node);
    this.diagramService.addClusterNode(type, node.id, resolvedName);
    if (options?.initialNodePatch) {
      this.diagramService.updateClusterNodes(node.id, options.initialNodePatch);
    }

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
    const rawType = (rawNode?.type || rawNode?.kind || this.fallbackIconType).toLowerCase();
    const type = rawType === 'customresource' || rawType === 'custom-resource' ? 'cr' : rawType;
    const normalizedIcon = this.resolveNodeIcon(type, rawNode?.icon);
    const normalized: DiagramNode = {
      id: rawNode?.id || uuidv4(),
      name: rawNode?.name || type,
      type,
      icon: normalizedIcon,
      x: typeof rawNode?.x === 'number' ? rawNode.x : 0,
      y: typeof rawNode?.y === 'number' ? rawNode.y : 0,
      width: typeof rawNode?.width === 'number' ? rawNode.width : this.nodeContentSize,
      height: typeof rawNode?.height === 'number' ? rawNode.height : this.nodeContentSize,
      isAffected: !!rawNode?.isAffected,
      hasHealthIssue: !!rawNode?.hasHealthIssue,
      healthReason: rawNode?.healthReason,
      replicas: typeof rawNode?.replicas === 'number' ? rawNode.replicas : undefined,
      rbacNodeType: String(rawNode?.rbacNodeType ?? '').toUpperCase() === 'ROLEBINDING' ? 'ROLEBINDING' : undefined,
      ...overrides
    };

    if (type === 'deployment') {
      const workloadSource = (overrides?.workloadType || rawNode?.workloadType) as string | undefined;
      normalized.workloadType = workloadSource ? (workloadSource.toString().toUpperCase() as DiagramNode['workloadType']) : 'DEPLOYMENT';
      normalized.command = this.normalizeStringArray(overrides?.command ?? rawNode?.command);
      normalized.args = this.normalizeStringArray(overrides?.args ?? rawNode?.args);
    } else {
      delete normalized.command;
      delete normalized.args;
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

  private normalizeStringArray(value: unknown): string[] {
    if (!Array.isArray(value)) {
      return [];
    }
    return value
      .map((entry) => String(entry ?? '').trim())
      .filter((entry) => !!entry);
  }

  private createNodes(): DragDropData[] {
    return [
      { name: 'Role', type: 'accesspolicy', baseName: 'role', icon: this.iconService.getIconPath('accesspolicy') },
      { name: 'RoleBinding', type: 'accesspolicy', baseName: 'role-binding', icon: this.iconService.getIconPath('rolebinding') },
      { name: 'ServiceAccount', type: 'serviceaccount', baseName: 'service-account', icon: this.iconService.getIconPath('serviceaccount') },
      { name: 'container', type: 'container', icon: this.iconService.getIconPath('container') },
      { name: 'deployment', type: 'deployment', icon: this.iconService.getIconPath('deployment') },
      { name: 'service', type: 'service', icon: this.iconService.getIconPath('service') },
      { name: 'cr', displayName: 'Custom Resource', baseName: 'custom-resource', type: 'cr', icon: this.iconService.getIconPath('cr') },
      { name: 'configbundle', displayName: 'Config bundle', baseName: 'config-bundle', type: 'configbundle', icon: this.iconService.getIconPath('configmap') },
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

  getServiceForwardBadge(node: DiagramNode): { label: string; title: string } | null {
    const type = (node?.type || '').toLowerCase();
    if (type !== 'service') {
      return null;
    }
    const active = Boolean((node as any)?.forwardActive);
    if (!active) {
      return null;
    }
    return { label: '>>', title: 'Port forward active' };
  }

  getWorkloadBadge(node: DiagramNode): { label: string; title: string } | null {
    if (node.type !== 'deployment') {
      return null;
    }
    const workload = (node.workloadType || 'DEPLOYMENT').toUpperCase();
    if (workload === 'STATEFULSET') {
      return { label: 'SS', title: $localize`:@@diagram.statefulSetWorkloadBadge:StatefulSet workload` };
    }
    if (workload === 'DAEMONSET') {
      return { label: 'DS', title: $localize`:@@diagram.daemonSetWorkloadBadge:DaemonSet workload` };
    }
    return null;
  }

  getAccessPolicyBadge(node: DiagramNode): { label: string; title: string; cssClass: string } | null {
    if (!this.isAccessPolicyNode(node)) {
      return null;
    }

    const snapshotNode = this.currentClusterSnapshot?.nodes?.find((n: any) => n.id === node.id) as any;
    const rbacNodeType = this.getAccessPolicyNodeType(node);
    const roleKind = String(snapshotNode?.roleKind ?? 'Role');
    const bindingKind = String(snapshotNode?.bindingKind ?? 'RoleBinding');

    if (rbacNodeType === 'ROLEBINDING' && bindingKind === 'ClusterRoleBinding') {
      return {
        label: 'CRB',
        title: $localize`:@@diagram.clusterRoleBindingBadge:ClusterRoleBinding`,
        cssClass: 'diagram-node__rbac-badge--cluster-role-binding'
      };
    }

    if (rbacNodeType === 'ROLE' && roleKind === 'ClusterRole') {
      return {
        label: 'CR',
        title: $localize`:@@diagram.clusterRoleBadge:ClusterRole`,
        cssClass: 'diagram-node__rbac-badge--cluster-role'
      };
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
      return $localize`:@@diagram.configBundleMixedTitle:Contains plain and secret entries`;
    }
    if (node.bundleMeta.hasSecretEntries) {
      return $localize`:@@diagram.configBundleSecretTitle:Contains secret entries`;
    }
    return $localize`:@@diagram.configBundlePlainTitle:Contains plain entries`;
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
      this.notificationService.warn(
        $localize`:@@diagram.namespaceRequiredTitle:Namespace required`,
        $localize`:@@diagram.namespaceRequiredDetail:Assign a namespace before opening a pod shell.`
      );
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
        this.podMenuError = $localize`:@@diagram.unableToLoadPods:Unable to load pods for this deployment.`;
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

  private refreshWorkloadHealth(): void {
    if (!this.isNamespaceDeployed() || !this.currentClusterSnapshot?.nameSpace) {
      this.applyWorkloadHealth([]);
      return;
    }
    this.kubeExplorerService.getWorkloadHealth(this.currentClusterSnapshot.nameSpace).pipe(
      catchError(() => of<WorkloadHealth[]>([]))
    ).subscribe(health => {
      this.applyWorkloadHealth(health);
    });
  }

  private applyWorkloadHealth(health: WorkloadHealth[]): void {
    this.workloadHealthMap.clear();
    health.forEach(entry => {
      if (entry?.kind && entry?.name) {
        this.workloadHealthMap.set(this.buildHealthKey(entry.kind, entry.name), entry);
      }
    });
    this.nodes = this.nodes.map(node => {
      const kind = this.mapNodeKindForHealth(node);
      if (!kind) {
        if (node.hasHealthIssue) {
          return { ...node, hasHealthIssue: false, healthReason: undefined };
        }
        return node;
      }
      const key = this.buildHealthKey(kind, node.name);
      const entry = this.workloadHealthMap.get(key);
      if (entry?.unhealthy) {
        return { ...node, hasHealthIssue: true, healthReason: entry.reason || 'Health check failed' };
      }
      if (node.hasHealthIssue) {
        return { ...node, hasHealthIssue: false, healthReason: undefined };
      }
      return node;
    });
  }

  private buildHealthKey(kind: string, name: string): string {
    return `${kind}:${name}`;
  }

  private mapNodeKindForHealth(node: DiagramNode): string {
    const type = node?.type?.toLowerCase() || '';
    if (type === 'statefulset' || type === 'daemonset' || type === 'deployment') {
      return type;
    }
    return '';
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

    if (this.isContainerAttachedToDeployment(node.id)) {
      return 'SIDECAR';
    }

    return undefined;
  }

  private resolveContainerRoleForLink(nodeA: DiagramNode, nodeB: DiagramNode): ContainerRole | undefined {
    const containerNode = nodeA.type === 'container' ? nodeA : nodeB.type === 'container' ? nodeB : null;
    if (!containerNode) {
      return undefined;
    }
    return this.resolveContainerRole(containerNode) ?? 'SIDECAR';
  }

  private isContainerAttachedToDeployment(containerNodeId: string): boolean {
    if (!containerNodeId) {
      return false;
    }
    if (!this.nodes?.length || !this.links?.length) {
      return false;
    }
    const nodeById = new Map(this.nodes.map(node => [node.id, node]));
    return this.links.some(link => {
      if (link.type !== 'Container') {
        return false;
      }
      if (link.from !== containerNodeId && link.to !== containerNodeId) {
        return false;
      }
      const otherId = link.from === containerNodeId ? link.to : link.from;
      return nodeById.get(otherId)?.type === 'deployment';
    });
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

    this.links = this.links.map(link => this.normalizeLinkOrientation(link));
    if (this.selectedLink) {
      this.selectedLink = this.links.find(l => l.id === this.selectedLink!.id) ?? null;
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
    const normalized = this.normalizeLinkOrientation(link);
    if (normalized.from !== link.from || normalized.to !== link.to || normalized.type !== link.type || normalized.note !== link.note) {
      Object.assign(link, normalized);
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
    line.setAttribute('stroke-dasharray', link.type === 'Use' ? '6 3' : '');
    line.setAttribute('data-direction', link.type === 'Use' ? 'reverse' : 'forward');
    line.setAttribute('title', link.type === 'Use' ? 'Use' : 'Expose');
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
    this.links = this.links.map(link => this.normalizeLinkOrientation(link));
    if (this.selectedLink) {
      this.selectedLink = this.links.find(l => l.id === this.selectedLink!.id) ?? null;
    }
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
    const normalized = this.normalizeLinkOrientation(link);
    Object.assign(link, normalized);
    this.selectedLink = this.links.find(l => l.id === link.id) ?? normalized;
    this.selectedNode = null; // Clear node selection when selecting a link
    this.diagramService.setSelectedLink(link.id);
    this.updateLinkStyles();
  }

  clearSelection() {
    this.selectedNode = null;
    this.selectedLink = null;
    this.updateLinkStyles();
    this.diagramService.clearSelectedNode();
    this.diagramService.clearSelectedLink();
  }

  updateLinkStyles() {
    this.links.forEach(link => {
      if (link.element) {
        const isSelected = this.selectedLink?.id === link.id;
        link.element.setAttribute('stroke', isSelected ? '#ff4444' : 'lightblue');
        link.element.setAttribute('stroke-width', isSelected ? '4' : '3');
        link.element.setAttribute('stroke-dasharray', link.type === 'Use' ? '6 3' : '');
        link.element.setAttribute('data-direction', link.type === 'Use' ? 'reverse' : 'forward');
        const title = link.type === 'Use'
          ? 'Use'
          : link.type === 'Container'
            ? 'Container'
            : link.type === 'serviceAccountBinding'
              ? 'ServiceAccount binding'
              : link.type === 'appliesTo'
                ? 'Applies to'
              : 'Expose';
        link.element.setAttribute('title', title);
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

    const normalizedSelected = this.normalizeLinkOrientation(this.selectedLink);

    // Remove the SVG element
    if (this.selectedLink.element) {
      this.selectedLink.element.remove();
    }

    // Remove from links array and re-render
    this.links = this.links.filter(link => link.id !== this.selectedLink!.id);
    this.selectedLink = null;
    this.diagramService.clearSelectedLink();
    this.renderLinks();
    if (this.resolveLinkType(normalizedSelected.type) === 'serviceAccountBinding') {
      const workloadId = normalizedSelected.to;
      const remaining = this.links
        .map(link => this.normalizeLinkOrientation(link))
        .find(link => this.resolveLinkType(link.type) === 'serviceAccountBinding' && link.to === workloadId);
      this.diagramService.updateClusterNodes(workloadId, { serviceAccountRef: remaining ? remaining.from : null });
    }
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

    connectedLinks
      .map(link => this.normalizeLinkOrientation(link))
      .filter(link => this.resolveLinkType(link.type) === 'serviceAccountBinding')
      .forEach(link => {
        this.diagramService.updateClusterNodes(link.to, { serviceAccountRef: null });
      });

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
        .filter((node: any) => {
          const type = ((node?.type || node?.kind || '') as string).toLowerCase();
          return type !== 'pod' && !this.hiddenNodeTypes.has(type);
        })
        .map((node: any) => this.normalizeDiagramNode(node));

      const parsedLinks = Array.isArray(data.links) ? data.links : [];
      this.links = parsedLinks
        .filter((link: any) => {
          return link.from && link.to &&
            this.nodes.some(node => node.id === link.from) &&
            this.nodes.some(node => node.id === link.to);
        })
        .map((link: any) => ({
          ...link,
          id: link?.id || uuidv4(),
          type: this.resolveLinkType(link?.type),
          note: typeof link?.note === 'string' ? link.note : undefined,
          containerRole: toContainerRole(link?.containerRole) || undefined
        })) as DiagramLink[];
      this.enforceLinkOrientation();
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
    options?: { skipUndo?: boolean; deferUpdate?: boolean; type?: LinkType; note?: string }
  ) {
    const fromNode = this.nodes.find(node => node.id === fromNodeId);
    const toNode = this.nodes.find(node => node.id === toNodeId);

    if (!fromNode || !toNode) {
      return;
    }

    if (!this.isContainerLinkAllowed(fromNode, toNode)) {
      this.notificationService.warn(
        $localize`:@@diagram.invalidConnectionTitle:Invalid connection`,
        $localize`:@@diagram.invalidContainerConnection:Containers can only be linked to Deployments or Config Bundles.`
      );
      return;
    }

    const involvesAccessPolicy = this.isAccessPolicyNode(fromNode) || this.isAccessPolicyNode(toNode);
    if (involvesAccessPolicy) {
      const policyNode = this.isAccessPolicyNode(fromNode) ? fromNode : toNode;
      const targetNode = policyNode.id === fromNode.id ? toNode : fromNode;
      const roleToWorkload = this.isRolePolicyNode(policyNode) && this.isWorkloadForAccessPolicy(targetNode);
      const roleBindingToServiceAccount = this.isRoleBindingPolicyNode(policyNode) && this.isServiceAccountNode(targetNode);
      const roleBindingToRole = this.isRoleBindingPolicyNode(policyNode) && this.isRolePolicyNode(targetNode);
      if (!roleToWorkload && !roleBindingToServiceAccount && !roleBindingToRole) {
        this.notificationService.warn(
          $localize`:@@diagram.invalidConnectionTitle:Invalid connection`,
          $localize`:@@diagram.invalidRoleBindingConnection:RoleBinding supports only links to Role and ServiceAccount. Role supports only workloads.`
        );
        return;
      }
    }

    const involvesServiceAccount = this.isServiceAccountNode(fromNode) || this.isServiceAccountNode(toNode);
    const involvesServiceAccountWorkloadBinding =
      (this.isServiceAccountNode(fromNode) && this.isServiceAccountSupportedWorkloadNode(toNode)) ||
      (this.isServiceAccountNode(toNode) && this.isServiceAccountSupportedWorkloadNode(fromNode));
    const involvesServiceAccountRoleBinding =
      (this.isServiceAccountNode(fromNode) && this.isRoleBindingPolicyNode(toNode)) ||
      (this.isServiceAccountNode(toNode) && this.isRoleBindingPolicyNode(fromNode));

    if (involvesServiceAccount && !involvesServiceAccountWorkloadBinding && !involvesServiceAccountRoleBinding) {
      this.notificationService.warn(
        $localize`:@@diagram.invalidConnectionTitle:Invalid connection`,
        $localize`:@@diagram.invalidServiceAccountConnection:ServiceAccounts can only be linked to workloads or RoleBinding.`
      );
      return;
    }

    const involvesDeploymentAndContainer =
      (fromNode.type === 'deployment' && toNode.type === 'container') ||
      (fromNode.type === 'container' && toNode.type === 'deployment');

    const involvesAccessPolicyWorkloadBinding =
      (this.isRolePolicyNode(fromNode) && this.isWorkloadForAccessPolicy(toNode)) ||
      (this.isRolePolicyNode(toNode) && this.isWorkloadForAccessPolicy(fromNode));
    const involvesRoleBindingRefs =
      (this.isRoleBindingPolicyNode(fromNode) && (this.isRolePolicyNode(toNode) || this.isServiceAccountNode(toNode))) ||
      (this.isRoleBindingPolicyNode(toNode) && (this.isRolePolicyNode(fromNode) || this.isServiceAccountNode(fromNode)));

    const type = involvesDeploymentAndContainer
      ? 'Container'
      : (involvesAccessPolicyWorkloadBinding || involvesRoleBindingRefs)
        ? 'appliesTo'
        : involvesServiceAccountWorkloadBinding
          ? 'serviceAccountBinding'
          : this.resolveLinkType(options?.type);
    const containerRole = involvesDeploymentAndContainer ? this.resolveContainerRoleForLink(fromNode, toNode) : undefined;
    const note = typeof options?.note === 'string' ? options.note : undefined;
    const oriented = this.orientLinkByType(fromNode, toNode, fromPoint, toPoint, type);
    const sourceId = oriented.fromNode.id;
    const targetId = oriented.toNode.id;

    if (type === 'serviceAccountBinding') {
      const workloadId = targetId;
      const existingBinding = this.links
        .map(link => this.normalizeLinkOrientation(link))
        .find(link => this.resolveLinkType(link.type) === 'serviceAccountBinding' && link.to === workloadId);
      if (existingBinding && existingBinding.from !== sourceId) {
        this.notificationService.warn(
          $localize`:@@diagram.serviceAccountAlreadySelectedTitle:ServiceAccount already selected`,
          $localize`:@@diagram.onlyOneServiceAccountPerWorkload:Each workload may reference at most one ServiceAccount.`
        );
        return;
      }
    }

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
      type,
      note,
      ...(containerRole ? { containerRole } : {}),
      fromPoint: oriented.fromPoint,
      toPoint: oriented.toPoint
    };

    this.links.push(link);
    this.renderLink(link);
    if (type === 'serviceAccountBinding') {
      this.diagramService.updateClusterNodes(targetId, { serviceAccountRef: sourceId });
    }
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

  private syncLinkMetadataFromCluster(): void {
    if (!this.currentClusterSnapshot?.links?.length || !this.links?.length) {
      return;
    }

    const nodeById = new Map(this.nodes.map(node => [node.id, node]));
    const isDeploymentContainerLink = (from: string, to: string): boolean => {
      const fromType = nodeById.get(from)?.type;
      const toType = nodeById.get(to)?.type;
      return (fromType === 'deployment' && toType === 'container') || (fromType === 'container' && toType === 'deployment');
    };

    const linkMap = new Map<string, any>();
    this.currentClusterSnapshot.links.forEach((link: any) => {
      const id = link.id || `${link.source}->${link.target}`;
      if (id) {
        linkMap.set(id, link);
      }
    });

    let hasChanges = false;
    this.links = this.links.map(link => {
      const match = linkMap.get(link.id) || linkMap.get(`${link.from}->${link.to}`);
      if (!match) {
        return { ...link, type: link.type ?? 'Expose' };
      }
      const matchType = String(match.type ?? '').trim();
      const lowerMatchType = matchType.toLowerCase();
      const normalizedType: LinkType = matchType === 'Use'
        ? 'Use'
        : matchType === 'Container'
          ? 'Container'
          : lowerMatchType === 'serviceaccountbinding'
            ? 'serviceAccountBinding'
            : lowerMatchType === 'appliesto'
              ? 'appliesTo'
            : 'Expose';
      const effectiveType: LinkType = isDeploymentContainerLink(link.from, link.to) ? 'Container' : normalizedType;
      const normalizedRole = toContainerRole((match as any).containerRole);
      const next: DiagramLink = {
        ...link,
        type: effectiveType,
        note: match.note,
        ...(normalizedRole && effectiveType === 'Container' ? { containerRole: normalizedRole } : {})
      };
      if (!normalizedRole || effectiveType !== 'Container') {
        delete (next as any).containerRole;
      }
      const oriented = this.normalizeLinkOrientation(next);
      if (
        oriented.type !== link.type ||
        oriented.note !== link.note ||
        oriented.containerRole !== link.containerRole ||
        oriented.from !== link.from ||
        oriented.to !== link.to
      ) {
        hasChanges = true;
        return oriented;
      }
      return oriented;
    });

    if (hasChanges) {
      if (this.selectedLink) {
        this.selectedLink = this.links.find(l => l.id === this.selectedLink!.id) ?? null;
      }
      this.renderLinks();
      this.updateDiagramData();
    }
  }

  private applyLinkUpdate(linkId: string, changes: { type: LinkType; note?: string; containerRole?: ContainerRole; clearContainerRole?: boolean }): void {
    const index = this.links.findIndex(link => link.id === linkId);
    if (index === -1) {
      return;
    }

    const current = this.links[index];
    const fromNode = this.nodes.find(n => n.id === current.from);
    const toNode = this.nodes.find(n => n.id === current.to);

    if (fromNode && toNode) {
      const involvesAccessPolicy = this.isAccessPolicyNode(fromNode) || this.isAccessPolicyNode(toNode);
      if (involvesAccessPolicy) {
        const policyNode = this.isAccessPolicyNode(fromNode) ? fromNode : toNode;
        const otherNode = policyNode.id === fromNode.id ? toNode : fromNode;
        const forcedType: LinkType | null =
          (this.isRolePolicyNode(policyNode) && this.isWorkloadForAccessPolicy(otherNode))
            || (this.isRoleBindingPolicyNode(policyNode) && this.isServiceAccountNode(otherNode))
            || (this.isRoleBindingPolicyNode(policyNode) && this.isRolePolicyNode(otherNode))
            ? 'appliesTo'
            : null;
        if (!forcedType) {
          this.notificationService.warn(
            $localize`:@@diagram.invalidConnectionTitle:Invalid connection`,
            $localize`:@@diagram.invalidRoleBindingConnection:RoleBinding supports only links to Role and ServiceAccount. Role supports only workloads.`
          );
          return;
        }
        changes = { ...changes, type: forcedType };
      } else if (changes.type === 'appliesTo') {
        this.notificationService.warn(
          $localize`:@@diagram.invalidLinkTypeTitle:Invalid link type`,
          $localize`:@@diagram.onlyRoleLinksAppliesTo:Only Role links can use Applies to.`
        );
        changes = { ...changes, type: current.type ?? 'Expose' };
      }

      const involvesDeploymentAndContainer =
        (fromNode.type === 'deployment' && toNode.type === 'container') ||
        (fromNode.type === 'container' && toNode.type === 'deployment');
      if (involvesDeploymentAndContainer) {
        changes = { ...changes, type: 'Container' };
      }
    }

    const updated: DiagramLink = {
      ...current,
      type: changes.type ?? current.type ?? 'Expose',
      note: 'note' in changes ? changes.note : current.note
    };

    if (changes.clearContainerRole) {
      delete (updated as any).containerRole;
    } else if ('containerRole' in changes) {
      const normalizedRole = toContainerRole((changes as any).containerRole);
      if (normalizedRole) {
        (updated as any).containerRole = normalizedRole;
      }
    }
    if (updated.type !== 'Container' && 'containerRole' in updated) {
      delete (updated as any).containerRole;
    }

    const oriented = this.normalizeLinkOrientation(updated);
    this.links[index] = oriented;
    if (this.selectedLink?.id === linkId) {
      this.selectedLink = oriented;
    }
    this.renderLinks();
    this.updateDiagramData();
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

  private isServiceAccountNode(node: DiagramNode): boolean {
    return (node?.type || '').toLowerCase() === 'serviceaccount';
  }

  private isAccessPolicyNode(node: DiagramNode): boolean {
    return (node?.type || '').toLowerCase() === 'accesspolicy';
  }

  private getAccessPolicyNodeType(node: DiagramNode): 'ROLE' | 'ROLEBINDING' {
    if (!this.isAccessPolicyNode(node)) {
      return 'ROLE';
    }
    if (node?.rbacNodeType === 'ROLEBINDING') {
      return 'ROLEBINDING';
    }
    const snapshotNode = this.currentClusterSnapshot?.nodes?.find((n: any) => n.id === node.id);
    const rawType = String((snapshotNode as any)?.rbacNodeType ?? 'ROLE').toUpperCase();
    return rawType === 'ROLEBINDING' ? 'ROLEBINDING' : 'ROLE';
  }

  private isRoleBindingPolicyNode(node: DiagramNode): boolean {
    return this.isAccessPolicyNode(node) && this.getAccessPolicyNodeType(node) === 'ROLEBINDING';
  }

  private isRolePolicyNode(node: DiagramNode): boolean {
    return this.isAccessPolicyNode(node) && this.getAccessPolicyNodeType(node) !== 'ROLEBINDING';
  }

  private isWorkloadForAccessPolicy(node: DiagramNode): boolean {
    const normalized = (node?.type || '').toLowerCase();
    return normalized === 'deployment' || normalized === 'job';
  }

  private isServiceAccountSupportedWorkloadNode(node: DiagramNode): boolean {
    const normalized = (node?.type || '').toLowerCase();
    return normalized === 'deployment' || normalized === 'job';
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

  private orientLinkByType(
    startNode: DiagramNode,
    endNode: DiagramNode,
    startPoint?: ConnectionPoint,
    endPoint?: ConnectionPoint,
    type: LinkType = 'Expose'
  ): {
    fromNode: DiagramNode;
    toNode: DiagramNode;
    fromPoint?: ConnectionPoint;
    toPoint?: ConnectionPoint;
  } {
    const normalizedType: LinkType =
      type === 'Use'
        ? 'Use'
        : type === 'Container'
          ? 'Container'
          : type === 'serviceAccountBinding'
            ? 'serviceAccountBinding'
            : type === 'appliesTo'
              ? 'appliesTo'
                : 'Expose';
    const isService = (node: DiagramNode) => node.type?.toLowerCase() === 'service';
    const isDeployment = (node: DiagramNode) => node.type?.toLowerCase() === 'deployment';
    const isServiceAccount = (node: DiagramNode) => node.type?.toLowerCase() === 'serviceaccount';
    const isWorkload = (node: DiagramNode) => this.isServiceAccountSupportedWorkloadNode(node);
    const isAccessPolicy = (node: DiagramNode) => node.type?.toLowerCase() === 'accesspolicy';
    const isPolicyWorkload = (node: DiagramNode) => this.isWorkloadForAccessPolicy(node);

    if (normalizedType === 'serviceAccountBinding') {
      if (isServiceAccount(startNode) && isWorkload(endNode)) {
        return { fromNode: startNode, toNode: endNode, fromPoint: startPoint, toPoint: endPoint };
      }
      if (isServiceAccount(endNode) && isWorkload(startNode)) {
        return { fromNode: endNode, toNode: startNode, fromPoint: endPoint, toPoint: startPoint };
      }
    }

    if (normalizedType === 'appliesTo') {
      if (this.isRoleBindingPolicyNode(startNode) && isServiceAccount(endNode)) {
        return { fromNode: startNode, toNode: endNode, fromPoint: startPoint, toPoint: endPoint };
      }
      if (this.isRoleBindingPolicyNode(endNode) && isServiceAccount(startNode)) {
        return { fromNode: endNode, toNode: startNode, fromPoint: endPoint, toPoint: startPoint };
      }
      if (this.isRoleBindingPolicyNode(startNode) && this.isRolePolicyNode(endNode)) {
        return { fromNode: startNode, toNode: endNode, fromPoint: startPoint, toPoint: endPoint };
      }
      if (this.isRoleBindingPolicyNode(endNode) && this.isRolePolicyNode(startNode)) {
        return { fromNode: endNode, toNode: startNode, fromPoint: endPoint, toPoint: startPoint };
      }
      if (isAccessPolicy(startNode) && isPolicyWorkload(endNode)) {
        return { fromNode: startNode, toNode: endNode, fromPoint: startPoint, toPoint: endPoint };
      }
      if (isAccessPolicy(endNode) && isPolicyWorkload(startNode)) {
        return { fromNode: endNode, toNode: startNode, fromPoint: endPoint, toPoint: startPoint };
      }
    }

    if (isService(startNode) && isDeployment(endNode)) {
      return normalizedType === 'Use'
        ? { fromNode: endNode, toNode: startNode, fromPoint: endPoint, toPoint: startPoint }
        : { fromNode: startNode, toNode: endNode, fromPoint: startPoint, toPoint: endPoint };
    }

    if (isService(endNode) && isDeployment(startNode)) {
      return normalizedType === 'Use'
        ? { fromNode: startNode, toNode: endNode, fromPoint: startPoint, toPoint: endPoint }
        : { fromNode: endNode, toNode: startNode, fromPoint: endPoint, toPoint: startPoint };
    }

    const dependencyOriented = this.orientLinkByDependency(startNode, endNode, startPoint, endPoint);

    if (normalizedType === 'Use') {
      return {
        fromNode: dependencyOriented.toNode,
        toNode: dependencyOriented.fromNode,
        fromPoint: dependencyOriented.toPoint,
        toPoint: dependencyOriented.fromPoint
      };
    }

    return dependencyOriented;
  }

  private normalizeLinkOrientation(link: DiagramLink): DiagramLink {
    const fromNode = this.nodes.find(n => n.id === link.from);
    const toNode = this.nodes.find(n => n.id === link.to);
    const normalizedType = this.resolveLinkType(link.type);

    if (!fromNode || !toNode) {
      return { ...link, type: normalizedType };
    }

    const oriented = this.orientLinkByType(fromNode, toNode, link.fromPoint, link.toPoint, normalizedType);

    return {
      ...link,
      type: normalizedType,
      from: oriented.fromNode.id,
      to: oriented.toNode.id,
      containerRole: link.containerRole,
      fromPoint: oriented.fromPoint,
      toPoint: oriented.toPoint
    };
  }

  private resolveLinkType(type?: any): LinkType {
    const value = String(type ?? '').trim();
    if (value === 'Use') {
      return 'Use';
    }
    if (value === 'Container') {
      return 'Container';
    }
    if (value.toLowerCase() === 'serviceaccountbinding') {
      return 'serviceAccountBinding';
    }
    if (value.toLowerCase() === 'appliesto') {
      return 'appliesTo';
    }
    return 'Expose';
  }

  private resolveIconPath(type?: string): string {
    const normalized = type?.toLowerCase() || this.fallbackIconType;
    return this.iconService.getIconPath(normalized) || this.iconService.getIconPath(this.fallbackIconType) || '';
  }

  private resolveNodeIcon(type: string, rawIcon?: string): string {
    const normalizedType = (type || '').toLowerCase();
    // Keep config resource icons deterministic to avoid stale/mismatched legacy icons (e.g. CR icon on ConfigMap).
    if (normalizedType === 'configmap' || normalizedType === 'configbundle') {
      return this.resolveIconPath('configmap');
    }
    if (normalizedType === 'secret') {
      return this.resolveIconPath('secret');
    }
    if (normalizedType === 'cr') {
      return this.resolveIconPath('cr');
    }
    return rawIcon || this.resolveIconPath(normalizedType);
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

    const baseNodes: any[] = Array.isArray(cluster.nodes)
      ? cluster.nodes.filter((node: any) => {
        const type = String(node?.type ?? node?.kind ?? '').toLowerCase();
        return !this.hiddenNodeTypes.has(type);
      })
      : [];
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

      const linkType = this.resolveLinkType((link as any)?.type);
      const linkNote = typeof (link as any)?.note === 'string' ? (link as any).note : undefined;
      const oriented = this.orientLinkByType(sourceNode, targetNode, (link as any).fromPoint, (link as any).toPoint, linkType);
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
        id: (link as any)?.id || uuidv4(),
        from: oriented.fromNode.id,
        to: oriented.toNode.id,
        type: linkType,
        note: linkNote,
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
      const linkType = this.resolveLinkType((link as any)?.type);
      const linkNote = typeof (link as any)?.note === 'string' ? (link as any).note : undefined;
      const oriented = this.orientLinkByType(sourceNode, targetNode, (link as any).fromPoint, (link as any).toPoint, linkType);
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
        id: (link as any)?.id || uuidv4(),
        from: oriented.fromNode.id,
        to: oriented.toNode.id,
        type: linkType,
        note: linkNote,
        fromPoint,
        toPoint
      });
    });

    return diagramLinks;
  }

  private enforceLinkOrientation(): void {
    this.links = this.links.map(link => this.normalizeLinkOrientation(link));
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
        id: l.id, from: l.from, to: l.to, type: l.type, note: l.note, fromPoint: l.fromPoint, toPoint: l.toPoint 
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
      const kind = typeof entry.kind === 'string' ? entry.kind.toLowerCase() : '';
      if (this.manifestKindsWithoutNodes.has(kind)) {
        return true;
      }
      const name = typeof entry.name === 'string' ? entry.name : undefined;
      return !!name && validNames.has(name);
    });

    const normalizedLinks = this.links.map(link => this.normalizeLinkOrientation(link));
    this.links = normalizedLinks;
    if (this.selectedLink) {
      this.selectedLink = this.links.find(l => l.id === this.selectedLink!.id) ?? null;
    }

    const serializedLinks = normalizedLinks.map(l => ({
      id: l.id,
      from: l.from,
      to: l.to,
      type: l.type,
      note: l.note,
      ...(l.containerRole ? { containerRole: l.containerRole } : {}),
      fromPoint: l.fromPoint,
      toPoint: l.toPoint
    }));

    const clusterLinks = normalizedLinks.map(l => new Link({
      id: l.id,
      source: l.from,
      target: l.to,
      type: l.type,
      note: l.note,
      containerRole: l.containerRole
    }));

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
        ...(n.type === 'accesspolicy' && n.rbacNodeType ? { rbacNodeType: n.rbacNodeType } : {}),
        ...(n.type === 'container' && n.role ? { role: n.role } : {}),
        ...(n.type === 'deployment' && n.workloadType ? { workloadType: n.workloadType } : {}),
        ...(n.type === 'deployment' && Array.isArray(n.command) ? { command: n.command } : {}),
        ...(n.type === 'deployment' && Array.isArray(n.args) ? { args: n.args } : {})
      })),
      links: serializedLinks,
      rawManifests: this.rawManifests
    });

    this.store.dispatch(actions.updateDiagram({ diagramData, links: clusterLinks }));
    this.updateSurfaceSize();
  }

  private persistImportedCluster(): void {
    this.store.select(getCurrentCluster).pipe(take(1)).subscribe(cluster => {
      if (!cluster?.id) {
        return;
      }
      this.clusterService.saveCluster(cluster).pipe(take(1)).subscribe({
        next: (savedCluster) => {
          if (savedCluster) {
            this.store.dispatch(actions.loadCluster({ cluster: savedCluster }));
          }
        },
        error: (error) => {
          const detail = error?.error || error?.message || $localize`:@@diagram.importPersistFailedDetail:Diagram imported but not saved.`;
          this.notificationService.warn(
            $localize`:@@diagram.importPersistFailedTitle:Import not persisted`,
            typeof detail === 'string' ? detail : undefined
          );
        }
      });
    });
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
    if (this.testHarnessRegistered) {
      const win = window as any;
      delete win.izyAddNode;
      delete win.izyConnect;
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

  private registerTestHarness(): void {
    if (this.testHarnessRegistered || !(window as any)?.Cypress) {
      return;
    }
    const win = window as any;
    win.izyAddNode = (type: string, options?: { name?: string; x?: number; y?: number }) => {
      return this.zone.run(() => {
        const normalizedType = (type || '').toLowerCase();
        const baseName = normalizedType === 'configbundle' ? 'config-bundle' : normalizedType || 'node';
        const preferredName = options?.name;
        const icon = this.resolveIconPath(normalizedType);
        const node = this.createNode(
          normalizedType,
          baseName,
          icon,
          options?.x ?? 320,
          options?.y ?? 320,
          { preferredName }
        );
        this.selectNode(node);
        return node;
      });
    };
    win.izyConnect = (fromName: string, toName: string, options?: { type?: LinkType }) => {
      return this.zone.run(() => {
        const fromNode = this.nodes.find(n => n.name === fromName);
        const toNode = this.nodes.find(n => n.name === toName);
        if (!fromNode || !toNode) {
          return false;
        }
        const fromPoint = this.getConnectionPoints(fromNode)[1];
        const toPoint = this.getConnectionPoints(toNode)[3];
        this.createLinkWithPoints(fromNode.id, toNode.id, fromPoint, toPoint, { type: options?.type });
        return true;
      });
    };
    this.testHarnessRegistered = true;
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
