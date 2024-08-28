import { ClusterState } from './../store/states/state';
import { DiagramService } from './../services/diagram.service';
import { IconService } from './../services/icon.service';
import { AfterViewInit, Component, ElementRef, HostListener, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import * as go from 'gojs';
import { Store, select } from '@ngrx/store';
import { v4 as uuidv4 } from 'uuid';
import { BehaviorSubject, Subscription, debounceTime, distinctUntilChanged, filter, startWith, take, tap } from 'rxjs';
import * as actions from '../store/actions/actions';
import { getCurrentCluster, selectClusterDiagram } from '../store/selectors/selectors';
import { Cluster } from '../model/cluster.class';

const $ = go.GraphObject.make;

@Component({
  selector: 'app-diagram',
  templateUrl: './diagram.component.html',
  styleUrls: ['./diagram.component.scss']
})
export class DiagramComponent implements OnInit, OnDestroy {

  @ViewChild('container', { static: true }) container!: ElementRef;
  diagram!: go.Diagram;
  model!: go.Model;
  isResizing: boolean = false;
  firstColumnWidth: number = 180;
  minWidth: number = 200; // Minimum width of the first column in pixels
  subscription: Subscription = new Subscription();

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
        if(diagramData!==null && diagramData!==undefined && diagramData!==""){
        this.model = go.Model.fromJson(diagramData);
        }
        this.createDiagram();
      }),
     take(1)
    ).subscribe();
  }


  private createDiagram() {

    this.diagram = $(go.Diagram, 'myDiagramDiv',
      {
        'undoManager.isEnabled': true,
        "linkingTool.isEnabled": true,
        "relinkingTool.isEnabled": true,
        "textEditingTool.isEnabled": true,
        "allowMove": true,

        grid:
          $(go.Panel, "Grid",
            { gridCellSize: new go.Size(10, 10) },
            $(go.Shape, "LineH", { strokeDashArray: [1, 9] })
          )
      });

    this.diagram.nodeTemplate = this.makeNodeTemplate();
    this.diagram.commandHandler.deletesTree = true;
    this.diagram.commandHandler.canDeleteSelection = () => true;

    //create empty model if not present
    this.model && (this.diagram.model = this.model);
    const graphLinksModel: go.GraphLinksModel = this.diagram.model as go.GraphLinksModel;

    this.diagram.linkTemplate =
      $(go.Link,
        {
          reshapable: true,
          resegmentable: true,
          relinkableFrom: true,
          relinkableTo: true,
          adjusting: go.Link.Stretch
        },
        new go.Binding("points").makeTwoWay(),
        new go.Binding("fromSpot", "fromSpot", go.Spot.parse).makeTwoWay(go.Spot.stringify),
        new go.Binding("toSpot", "toSpot", go.Spot.parse).makeTwoWay(go.Spot.stringify),
        $(go.Shape, { fill: 'lightblue', strokeWidth: 3 }),  // Link appearance
        $(go.Shape, { fill: 'lightblue', strokeWidth: 3, toArrow: 'Standard' })
      );

    // Attach event handlers
    this.addEventHanlders(graphLinksModel);
    this.setLinkingRules();
  }


  private addEventHanlders(graphLinksModel: go.GraphLinksModel) {
    this.diagram.addDiagramListener('LinkDrawn', (e) => {
      const link = e.subject;
      const fromNode = link.fromNode;
      const toNode = link.toNode;

      if (fromNode && toNode && fromNode instanceof go.Node && toNode instanceof go.Node) {
        graphLinksModel.addLinkData(link.data);
      }
      this.diagramService.onLinkDrawn(e)
    });

    this.diagram.addDiagramListener('ChangedSelection', e => this.diagramService.onSelectionChanged(e));
    this.diagram.addDiagramListener("TextEdited", e => this.diagramService.onNodeEdited(e));
    this.diagram.addDiagramListener("ExternalObjectsDropped", e => {
      this.diagram.startTransaction("dropExternalObjects");
      try {
        this.generateUUID(e);
        this.chooseUniqueNameForNode(e);
        this.diagramService.onNodeDropped(e);
        this.diagram.clearSelection();
        this.diagram.commitTransaction("dropExternalObjects");
      } catch (error) {
        console.error("Error during external object drop:", error);
        this.diagram.rollbackTransaction();
      }
    });
    this.diagram.addDiagramListener("SelectionDeleted", e => {
      this.diagramService.onNodeDeleted(e);
      this.diagram.clearSelection();
    });


    this.diagram.addDiagramListener('SelectionDeleting', e => this.diagramService.onNodeDeleted(e));

    this.diagram.addModelChangedListener(evt => {
      // ignore unimportant Transaction events
      if (!evt.isTransactionFinished) return;
      var txn = evt.object;  // a Transaction
      if (txn === null) return;
      // iterate over all of the actual ChangedEvents of the Transaction
      txn['changes'].each((e: { modelChange: string; change: go.EnumValue; }) => {
        // handle node changes
        if (e.modelChange === "nodeDataArray") {
          if (e.change === go.ChangedEvent.Insert) {
            // Dispatch action for node added
            this.store.dispatch(actions.updateDiagram({ diagramData: this.diagram.model.toJson() }));
          } else if (e.change === go.ChangedEvent.Remove) {
            // Dispatch action for node removed
            this.store.dispatch(actions.updateDiagram({ diagramData: this.diagram.model.toJson() }));
          }
        }
        // handle linkDTO changes
        else if (e.modelChange === "linkDataArray") {
          if (e.change === go.ChangedEvent.Insert) {
            // Dispatch action for linkDTO added
            this.store.dispatch(actions.updateDiagram({ diagramData: this.diagram.model.toJson() }));
          } else if (e.change === go.ChangedEvent.Remove) {
            // Dispatch action for linkDTO removed
            this.store.dispatch(actions.updateDiagram({ diagramData: this.diagram.model.toJson() }));
          }
        }
      });
    });
  }

  generateUUID(e: go.DiagramEvent) {
    const diagram = e.diagram;
    const droppedNode = e.subject.first();
    if (droppedNode instanceof go.Node) {
      // Start a transaction to modify the model
      diagram.startTransaction("assignUUID");
      const data = droppedNode.data;
      // Modify the model data within the transaction
      diagram.model.setDataProperty(data, "key", uuidv4());
      // Commit the transaction after making changes
      diagram.commitTransaction("assignUUID");
    }
  }

  chooseUniqueNameForNode(e: go.DiagramEvent) {
    e.subject.each((part: any) => {
      if (part instanceof go.Node) {
        const baseName = part.data.name;

        // Filter existing nodes in the diagram to find those with the same base name
        const similarNodes = this.diagram.model.nodeDataArray.filter((node: any) =>
          node.name && node.name.startsWith(baseName)
        );

        // Initialize the name with the base name
        let newName = baseName;
        let maxSuffixCharCode = 'a'.charCodeAt(0) - 1; // Start before 'a'

        // Extract and find the maximum suffix character used
        similarNodes.forEach(node => {
          const result = node['name'].match(/^(.*?)-([a-zA-Z])$/); // Match the pattern with the suffix as a letter
          if (result && result[2]) {
            const charCode = result[2].charCodeAt(0);
            if (charCode > maxSuffixCharCode) {
              maxSuffixCharCode = charCode; // Update to the maximum suffix char code found
            }
          }
        });

        // Determine the new name based on the maximum suffix found
        if (similarNodes.length > 0) {
          const newSuffixChar = String.fromCharCode(maxSuffixCharCode + 1); // Increment to the next character
          newName = `${baseName}-${newSuffixChar}`;
        }

        // Perform the renaming in a transaction
        this.diagram.model.startTransaction("rename node");
        this.diagram.model.setDataProperty(part.data, "name", newName);
        this.diagram.model.commitTransaction("rename node");
      }
    });
  }

  private createPalette() {
    const $ = go.GraphObject.make;

    // Node data array with icon URLs
    var nodeDataArray = this.createNodes();


    // Initialize the palette
    const myPalette =
      $(go.Palette, 'myPaletteDiv',
        {
          model: new go.GraphLinksModel(nodeDataArray,), // Pass the node data array to the model
          layout: $(go.GridLayout, { wrappingColumn: 1, cellSize: new go.Size(1, 1) }),
          grid: $(go.Panel, "Grid",
            { gridCellSize: new go.Size(10, 10) },
            $(go.Shape, "LineH", { strokeDashArray: [1, 9] })
          )
        },
      );

    myPalette.nodeTemplate = this.makeNodeTemplate();
  }

  private createNodes() {
    return [
      { name: 'ingress', type: 'ingress', icon: this.iconService.getIconPath('ingress') },
      { name: 'container', type: 'container', icon: this.iconService.getIconPath('container') },
      { name: 'pod', type: 'pod', icon: this.iconService.getIconPath('pod') },
      { name: 'deployment', type: 'deployment', icon: this.iconService.getIconPath('deployment') },
      { name: 'service', type: 'service', icon: this.iconService.getIconPath('service') },
      { name: 'configmap', type: 'configmap', icon: this.iconService.getIconPath('configmap') },
      { name: 'volume', type: 'volume', icon: this.iconService.getIconPath('volume') }
    ];
  }

  private setLinkingRules() {
    this.diagram.toolManager.linkingTool.linkValidation = (fromNode, fromPort, toNode, toPort) => {
      const fromType = fromNode.data.type;
      const toType = toNode.data.type;

      switch (fromType) {
        case 'configmap':
          return toType === 'pod' || toType === 'deployment';
        case 'service':
          return toType === 'deployment' || toType === 'pod';
        case 'ingress':
          return toType === 'service';
        case 'pod':
          return toType === 'deployment';
        case 'deployment':
          return false;
        case 'container':
          return toType === 'deployment' || toType === 'pod';
        default:
          return false;
      }
    };

    // Apply the same validation for relinking
    this.diagram.toolManager.relinkingTool.linkValidation = this.diagram.toolManager.linkingTool.linkValidation;
  }

  private makeNodeTemplate() {

    return $(go.Node, "Spot",
      {
        locationSpot: go.Spot.Center,
        movable: true
      },
      new go.Binding("location", "loc", go.Point.parse).makeTwoWay(go.Point.stringify),

      // Outer transparent rectangle larger than the node, acts as the linkable area
      this.makeLinkableArea(),
      // Inner node visual representation
      this.makeNodeBorder(),
      // Place the Picture (icon) inside the node shape
      this.makeNodeIcon(),
      // Node label inside the shape, below the icon
      this.makeNodeLabel()
    )
  }

  makeNodeBorder() {
    return $(go.Shape, "RoundedRectangle",
      {
        width: 60, height: 60, fill: 'white', strokeWidth: 3,
        cursor: "hand",
      }
    )
  }

  makeLinkableArea() {
    return $(go.Shape, "RoundedRectangle",
      {
        fill: "transparent",  // Transparent so it doesn't obscure the node
        stroke: null,  // No visible stroke
        strokeWidth: 0,
        width: 80, height: 80,  // Larger than the node to act as a linkable area
        portId: "",  // This shape acts as the port
        fromLinkable: true,
        toLinkable: true,
        cursor: "pointer"
      }
    )
  }

  makeNodeIcon() {
    return $(go.Picture,
      {
        width: 40, height: 40, margin: 5,
      },
     // new go.Binding("source", "icon") // Bind picture source to icon property in the node data
      new go.Binding("source", "icon")
    )
  }

  makeNodeLabel() {

    return $(go.TextBlock,
      {
        alignment: go.Spot.BottomCenter,
        margin: 5,
        editable: true,
        textAlign: "center",
        wrap: go.TextBlock.WrapFit,
        overflow: go.TextBlock.OverflowEllipsis
      },
      new go.Binding("text", "name")
    )
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
