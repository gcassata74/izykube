import { DiagramService } from './../services/diagram.service';
import { IconService } from './../services/icon.service';
import { Component, ElementRef, HostListener, OnChanges, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import * as go from 'gojs';
import { Store } from '@ngrx/store';
import { v4 as uuidv4 } from 'uuid';
import { BehaviorSubject } from 'rxjs';

const $ = go.GraphObject.make;
// a collection of colors
var colors = {
  blue: "#2a6dc0",
  orange: "#ea2857",
  green: "#1cc1bc",
  gray: "#5b5b5b",
  white: "#F5F5F5"
}


@Component({
  selector: 'app-diagram',
  templateUrl: './diagram.component.html',
  styleUrls: ['./diagram.component.scss']
})
export class DiagramComponent implements OnInit {

  @ViewChild('container', { static: true }) container!: ElementRef;
  diagram!: go.Diagram;
  isResizing: boolean = false;
  firstColumnWidth: number = 200;
  minWidth: number = 200; // Minimum width of the first column in pixels



  constructor(
    private iconService : IconService,
    private store: Store,
    private diagramService : DiagramService
    ) { }

  ngOnInit(): void {
    //write methods to create the gojs diagram
    this.createDiagram();
    //write a metohd to create the palette
    this.createPalette();
  }

  private createDiagram() {

    this.diagram = $(go.Diagram, 'myDiagramDiv',
     {
       'undoManager.isEnabled': true,
       "linkingTool.isEnabled": true,
       "relinkingTool.isEnabled": true,
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
  

     // define the diagram model with nodeDataArray and linkDataArray
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
         $(go.Shape, {fill: 'lightblue', strokeWidth: 3}),  // Link appearance
         $(go.Shape, {fill: 'lightblue', strokeWidth: 3, toArrow: 'Standard'})
       );
  
     // Attach event handlers
     this.addEventHanlders(graphLinksModel);
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
    this.diagram.addDiagramListener("ExternalObjectsDropped", e => {
      this.chooseUniqueNameForNode(e);
      this.diagramService.onNodeDropped(e);
      this.diagram.clearSelection();
    });
    this.diagram.addDiagramListener("SelectionDeleted", e => {
      this.diagramService.onNodeDeleted(e);
      this.diagram.clearSelection();
    });

    this.diagram.addDiagramListener('SelectionDeleting', e => this.diagramService.onNodeDeleted(e));
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
  
        if (similarNodes.length > 0) {
          // Extract and sort the suffix numbers of similar nodes, ensuring all are numbers
          const suffixNumbers = similarNodes.map(node => {
            const match = node['name'].match(new RegExp(`^${baseName}(\\d+)$`));
            return match ? parseInt(match[1], 10) : null;
          }).filter((number): number is number => number !== null).sort((a, b) => a - b);
  
          let nextSuffix = 1;
          if (suffixNumbers.length > 0) {
            for (let i = 0; i < suffixNumbers.length; i++) {
              if (nextSuffix < suffixNumbers[i]) {
                break; // Found a gap in the sequence
              }
              nextSuffix = suffixNumbers[i] + 1;
            }
          }
          newName = `${baseName}${nextSuffix}`;
        }
  
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
                model: new go.GraphLinksModel(nodeDataArray, ), // Pass the node data array to the model
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
      { key: uuidv4(), name: 'Ingress', type: 'ingress', icon: this.iconService.getIconPath('ingress') },
      { key: uuidv4(), name: 'Container', type: 'container', icon: this.iconService.getIconPath('container') },
      { key: uuidv4(), name: 'Pod', type: 'pod', icon: this.iconService.getIconPath('pod') },
      { key: uuidv4(), name: 'Deployment', type: 'deployment', icon: this.iconService.getIconPath('deployment') },
      { key: uuidv4(), name: 'Service', type: 'service', icon: this.iconService.getIconPath('service') },
      { key: uuidv4(), name: 'ConfigMap', type: 'configMap', icon: this.iconService.getIconPath('configMap') }
    ];
  }

private makeNodeTemplate() {
  const $ = go.GraphObject.make; // Define a shorthand variable for GoJS methods

  return $(go.Node, "Spot",  // Use 'Spot' for the main node panel
      {
          locationSpot: go.Spot.Center,
          movable: true
      },
      new go.Binding("location", "loc", go.Point.parse).makeTwoWay(go.Point.stringify),

      // Outer transparent rectangle larger than the node, acts as the linkable area
      $(go.Shape, "Rectangle",
          {
              fill: "transparent",  // Transparent so it doesn't obscure the node
              stroke: null,  // No visible stroke
              strokeWidth: 0,
              width: 80, height: 80,  // Larger than the node to act as a linkable area
              portId: "",  // This shape acts as the port
              fromLinkable: true, toLinkable: true, cursor: "pointer"
          }
      ),

      // Panel with a blue border and white background
      $(go.Panel, "Auto",
          {
              background: "white",  // Set the background color to white
              padding: 3,           // Add padding for the blue border
              defaultStretch: go.GraphObject.Fill
          },
          // Blue border around the panel
          $(go.Shape, "Rectangle",
              {
                  stroke: "#4747ff",       // Set the border color to blue
                  strokeWidth: 3,       // Set the border width to 3 pixels
                  stretch: go.GraphObject.Fill
              }
          ),
          // Inner node visual representation
          $(go.Shape, "Rectangle",
              {
                  width: 60, height: 60, fill: 'white', strokeWidth: 2,
                  cursor: "hand"
              }
          ),
          // Place the Picture (icon) inside the node shape
          $(go.Picture,
              {
                  width: 40, height: 40, margin: 5,
              },
              new go.Binding("source", "icon") // Bind picture source to icon property in the node data
          ),
          // Node label inside the shape, below the icon
      ),
      $(go.TextBlock,
        {
            alignment: go.Spot.Bottom, margin: 5, editable: true, textAlign: "center"
        },
        new go.Binding("text", "name")
    )
  );
}

}
