import { DiagramService } from './../services/diagram.service';
import { IconService } from './../services/icon.service';
import { AfterViewInit, Component, ElementRef, HostListener, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { Store, select } from '@ngrx/store';
import { v4 as uuidv4 } from 'uuid';
import { Subscription, debounceTime, filter, finalize, take, tap } from 'rxjs';
import * as actions from '../store/actions/actions';
import { getCurrentCluster, getNodeById, selectClusterDiagram } from '../store/selectors/selectors';
import { Cluster, ClusterExportMode } from '../model/cluster.class';
import { DragDropData, DropEvent } from '../directives/drag-drop.directive';
import { AiAssistantService, AiChatMessage, AiImportYamlResponse, AiExportYamlResponse, AiHelmChartExportResponse } from '../services/ai-assistant.service';
import { NotificationService } from '../services/notification.service';

interface DiagramNode {
  id: string;
  name: string;
  type: string;
  icon: string;
  x: number;
  y: number;
  element?: HTMLElement;
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
export class DiagramComponent implements OnInit, OnDestroy {

  @ViewChild('container', { static: true }) container!: ElementRef;
  @ViewChild('diagramCanvas', { static: true }) diagramCanvas!: ElementRef;
  @ViewChild('paletteContainer', { static: true }) paletteContainer!: ElementRef;

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
  private readonly nodeContentSize = 80;
  private readonly nodeBorderWidth = 3;

  constructor(
    private iconService: IconService,
    private store: Store,
    private diagramService: DiagramService,
    private aiAssistantService: AiAssistantService,
    private notificationService: NotificationService
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

    this.subscription.add(
      this.store.select(getCurrentCluster).pipe(
        tap(cluster => {
          this.currentClusterSnapshot = cluster ? Cluster.fromJSON(cluster) : null;
        })
      ).subscribe()
    );
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

  private initializePaletteItems() {
    this.paletteItems = this.createNodes();
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

  private fetchClusterExport(): void {
    if (!this.currentClusterSnapshot) {
      this.notificationService.warn('No cluster to export', 'Create or load a cluster before exporting YAML.');
      this.clusterYamlDialogVisible = false;
      return;
    }

    const payload = JSON.parse(JSON.stringify(this.currentClusterSnapshot));
    this.clusterYamlLoading = true;
    this.clusterYamlError = null;
    if (this.clusterExportMode === 'HELM_CHART') {
      this.aiAssistantService.exportHelmChart(payload).pipe(
        finalize(() => this.clusterYamlLoading = false)
      ).subscribe({
        next: (response: AiHelmChartExportResponse) => {
          this.clusterYamlText = '';
          this.helmChartBlob = response.blob;
          const fallbackName = `${this.sanitizeFileName(this.currentClusterSnapshot?.name || 'izykube-cluster')}-chart.zip`;
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
        this.clusterYamlFileName = `${this.sanitizeFileName(this.currentClusterSnapshot?.name || 'izykube-cluster')}.yaml`;
      },
      error: (error) => {
        this.handleClusterExportError(error);
      }
    });
  }

  private handleClusterExportError(error: any): void {
    const detail = error?.error || error?.message || 'Cluster export failed.';
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
      this.notificationService.warn('Add YAML', 'Paste cluster YAML before importing.');
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
        this.notificationService.success('Cluster imported', 'Diagram updated from YAML.');
        this.clusterYamlDialogVisible = false;
      },
      error: (error) => {
        const detail = error?.error || error?.message || 'Cluster import failed.';
        this.clusterYamlError = typeof detail === 'string' ? detail : 'Cluster import failed.';
      }
    });
  }

  copyExportedYaml(): void {
    if (!this.clusterYamlText) {
      return;
    }
    if (navigator && navigator.clipboard) {
      navigator.clipboard.writeText(this.clusterYamlText).then(
        () => this.notificationService.success('Copied', 'Cluster YAML copied to clipboard.'),
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
    link.download = this.clusterYamlFileName || 'izykube-cluster.yaml';
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
    link.download = this.clusterYamlFileName || `${this.sanitizeFileName(this.currentClusterSnapshot?.name || 'izykube-cluster')}-chart.zip`;
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
    this.aiAssistantService.importYaml({ yaml, name: 'AI Generated Cluster' }).subscribe({
      next: (response) => {
        this.applyImportedCluster(response);
        this.notificationService.success('Cluster imported', 'Diagram updated from YAML.');
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
    } else {
      this.nodes = (cluster.nodes as any) || [];
      this.links = (cluster.links as any) || [];
      this.rawManifests = [];
    }

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
      .replace(/^-+|-+$/g, '') || 'izykube-cluster';
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

    const resolvedName = options?.preferredName
      ? this.ensureUniqueName(options.preferredName)
      : this.generateUniqueName(baseName);

    const node: DiagramNode = {
      id: uuidv4(),
      name: resolvedName,
      type: type,
      icon: icon,
      x: x,
      y: y
    };

    this.nodes.push(node);
    this.diagramService.addClusterNode(type, node.id, resolvedName);

    if (!options?.deferUpdate) {
      this.updateDiagramData();
    }

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
    this.diagramService.updateClusterNodes(node.id, { name: node.name });
    this.updateDiagramData();
  }

  onNodeMouseDown(event: MouseEvent, node: DiagramNode) {
    // Prevent default to avoid text selection
    event.preventDefault();

    let isDragging = false;
    let hasSavedToUndo = false;
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
          if (!hasSavedToUndo) {
            this.saveToUndoStack();
            hasSavedToUndo = true;
          }
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

    // Use specific connection points if available, otherwise use node centers
    let fromPoint: { x: number, y: number };
    let toPoint: { x: number, y: number };

    if (link.fromPoint) {
      // Update the connection point coordinates based on current node position
      const updatedFromPoints = this.getConnectionPoints(fromNode);
      const matchingFromPoint = updatedFromPoints.find(p => p.side === link.fromPoint!.side);
      fromPoint = matchingFromPoint || this.getNodeCenter(fromNode);
    } else {
      fromPoint = this.getNodeCenter(fromNode);
    }

    if (link.toPoint) {
      // Update the connection point coordinates based on current node position
      const updatedToPoints = this.getConnectionPoints(toNode);
      const matchingToPoint = updatedToPoints.find(p => p.side === link.toPoint!.side);
      toPoint = matchingToPoint || this.getNodeCenter(toNode);
    } else {
      toPoint = this.getNodeCenter(toNode);
    }

    const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
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
        // Use specific connection points if available, otherwise use node centers
        let fromPoint: { x: number, y: number };
        let toPoint: { x: number, y: number };

        if (link.fromPoint) {
          const updatedFromPoints = this.getConnectionPoints(fromNode);
          const matchingFromPoint = updatedFromPoints.find(p => p.side === link.fromPoint!.side);
          fromPoint = matchingFromPoint || this.getNodeCenter(fromNode);
        } else {
          fromPoint = this.getNodeCenter(fromNode);
        }

        if (link.toPoint) {
          const updatedToPoints = this.getConnectionPoints(toNode);
          const matchingToPoint = updatedToPoints.find(p => p.side === link.toPoint!.side);
          toPoint = matchingToPoint || this.getNodeCenter(toNode);
        } else {
          toPoint = this.getNodeCenter(toNode);
        }
        
        link.element.setAttribute('x1', fromPoint.x.toString());
        link.element.setAttribute('y1', fromPoint.y.toString());
        link.element.setAttribute('x2', toPoint.x.toString());
        link.element.setAttribute('y2', toPoint.y.toString());
      }
    });
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
  onKeyDown(event: KeyboardEvent) {
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

  private deleteSelectedLink() {
    if (!this.selectedLink) return;

    this.saveToUndoStack();

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
      this.nodes = data.nodes || [];
      this.links = data.links || [];
      if (Array.isArray(data.rawManifests)) {
        this.rawManifests = data.rawManifests;
      }
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
    // Check if link already exists
    const existingLink = this.links.find(link =>
      (link.from === fromNodeId && link.to === toNodeId) ||
      (link.from === toNodeId && link.to === fromNodeId)
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
      from: fromNodeId,
      to: toNodeId,
      fromPoint: fromPoint,
      toPoint: toPoint
    };

    this.links.push(link);
    this.renderLink(link);
    if (!options?.deferUpdate) {
      this.updateDiagramData();
    }
  }

  private findClosestConnectionPoint(clientX: number, clientY: number, connectionPoints: ConnectionPoint[]): ConnectionPoint {
    const canvasRect = this.diagramCanvas.nativeElement.getBoundingClientRect();
    const x = clientX - canvasRect.left;
    const y = clientY - canvasRect.top;

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
    const halfSize = this.nodeContentSize / 2 + this.nodeBorderWidth;
    return {
      x: node.x + halfSize,
      y: node.y + halfSize
    };
  }

  getConnectionPoints(node: DiagramNode): ConnectionPoint[] {
    const halfSize = this.nodeContentSize / 2 + this.nodeBorderWidth;
    const totalSize = this.nodeContentSize + this.nodeBorderWidth * 2;
    return [
      { side: 'top', x: node.x + halfSize, y: node.y },
      { side: 'right', x: node.x + totalSize, y: node.y + halfSize },
      { side: 'bottom', x: node.x + halfSize, y: node.y + totalSize },
      { side: 'left', x: node.x, y: node.y + halfSize }
    ];
  }

  onConnectionPointClick(node: DiagramNode, point: ConnectionPoint, event: MouseEvent) {
    event.stopPropagation();
    event.preventDefault();

    if (!this.isConnecting) {
      // Start connection immediately with drag
      this.isConnecting = true;
      this.connectionStartNode = node;
      this.connectionStartPoint = point;
      
      // Create temporary line for visual feedback
      this.createTempLine(point.x, point.y, point.x, point.y);
      
      // Start dragging immediately
      this.startConnectionDrag();
    } else {
      // End connection - only allow if clicking on a different node's connection point
      if (this.connectionStartNode && this.connectionStartNode.id !== node.id) {
        this.createLinkWithPoints(
          this.connectionStartNode.id, 
          node.id,
          this.connectionStartPoint!,
          point
        );
      }
      this.cancelConnection();
    }
  }

  private startConnectionDrag() {
    this.isDraggingConnection = true;

    const onMouseMove = (moveEvent: MouseEvent) => {
      if (this.isConnecting && this.tempLine) {
        const canvasRect = this.diagramCanvas.nativeElement.getBoundingClientRect();
        const x = moveEvent.clientX - canvasRect.left;
        const y = moveEvent.clientY - canvasRect.top;
        
        this.tempLine.setAttribute('x2', x.toString());
        this.tempLine.setAttribute('y2', y.toString());
        
        // Highlight connection points when hovering over them
        this.highlightNearbyConnectionPoints(moveEvent.clientX, moveEvent.clientY);
      }
    };

    const onMouseUp = (upEvent: MouseEvent) => {
      if (this.isConnecting) {
        // Check if we're dropping on a connection point
        const targetElement = document.elementFromPoint(upEvent.clientX, upEvent.clientY);
        const connectionPoint = targetElement?.closest('.connection-point');
        
        if (connectionPoint) {
          // Find the target node and connection point
          const nodeElement = connectionPoint.closest('.diagram-node');
          if (nodeElement) {
            const targetNode = this.findNodeByElement(nodeElement as HTMLElement);
            
            if (targetNode && targetNode.id !== this.connectionStartNode?.id) {
              // Determine which connection point was targeted
              const targetConnectionPoints = this.getConnectionPoints(targetNode);
              const targetPoint = this.findClosestConnectionPoint(
                upEvent.clientX, 
                upEvent.clientY, 
                targetConnectionPoints
              );
              
              this.createLinkWithPoints(
                this.connectionStartNode!.id, 
                targetNode.id,
                this.connectionStartPoint!,
                targetPoint
              );
            }
          }
        }
        
        this.cancelConnection();
      }

      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    };

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  }

  private findNodeByElement(nodeElement: HTMLElement): DiagramNode | null {
    const leftStyle = nodeElement.style.left;
    const topStyle = nodeElement.style.top;
    
    if (!leftStyle || !topStyle) return null;
    
    const x = parseInt(leftStyle.replace('px', ''));
    const y = parseInt(topStyle.replace('px', ''));
    
    return this.nodes.find(node => node.x === x && node.y === y) || null;
  }

  private highlightNearbyConnectionPoints(clientX: number, clientY: number) {
    const threshold = 20; // pixels
    
    this.nodes.forEach(node => {
      if (node.id === this.connectionStartNode?.id) return; // Skip start node
      
      const connectionPoints = this.getConnectionPoints(node);
      connectionPoints.forEach(point => {
        const canvasRect = this.diagramCanvas.nativeElement.getBoundingClientRect();
        const pointScreenX = canvasRect.left + point.x;
        const pointScreenY = canvasRect.top + point.y;
        
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
    const nodeElement = document.querySelector(`[style*="left: ${node.x}px"][style*="top: ${node.y}px"]`);
    if (!nodeElement) return null;
    
    const connectionPoints = nodeElement.querySelectorAll('.connection-point');
    const sides = ['top', 'right', 'bottom', 'left'];
    const sideIndex = sides.indexOf(point.side);
    
    return connectionPoints[sideIndex] as HTMLElement || null;
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

  private cancelConnectionDrag() {
    this.isDraggingConnection = false;
    this.connectionStartNode = null;
    this.connectionStartPoint = null;
    
    if (this.tempLine) {
      this.tempLine.remove();
      this.tempLine = null;
    }
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
      this.cancelConnectionDrag();
    }
  }

  private saveToUndoStack() {
    // Deep clone current state
    const currentState = {
      nodes: JSON.parse(JSON.stringify(this.nodes.map(n => ({ 
        id: n.id, name: n.name, type: n.type, icon: n.icon, x: n.x, y: n.y 
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
    const validIds = new Set(this.nodes.map(n => n.id));
    this.rawManifests = (this.rawManifests || []).filter(entry => {
      if (!entry || typeof entry !== 'object') {
        return false;
      }
      const name = (entry as any).name;
      return typeof name === 'string' && validIds.has(name);
    });

    const diagramData = JSON.stringify({
      nodes: this.nodes.map(n => ({ id: n.id, name: n.name, type: n.type, icon: n.icon, x: n.x, y: n.y })),
      links: this.links.map(l => ({ 
        id: l.id, 
        from: l.from, 
        to: l.to,
        fromPoint: l.fromPoint,
        toPoint: l.toPoint
      })),
      rawManifests: this.rawManifests
    });

    this.store.dispatch(actions.updateDiagram({ diagramData }));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
