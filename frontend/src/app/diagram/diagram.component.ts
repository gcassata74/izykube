import { Component, ElementRef, HostListener, OnInit, ViewChild } from '@angular/core';
import * as go from 'gojs';

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
  firstColumnWidth: number = 200; // Initial width in pixels
  minWidth: number = 200; // Minimum width of the first column in pixels


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
 
     // define the diagram model with nodeDataArray and linkDataArray
     const graphLinksModel: go.GraphLinksModel = this.diagram.model as go.GraphLinksModel;
 
     this.diagram.linkTemplate =
       $(go.Link,
         {
           reshapable: true,
           resegmentable: true,
           relinkableFrom: true,
           relinkableTo: true,
           adjusting: go.Link.Stretch,
           toShortLength: 50,
           fromShortLength: 50
         },
         new go.Binding("points").makeTwoWay(),
         new go.Binding("fromSpot", "fromSpot", go.Spot.parse).makeTwoWay(go.Spot.stringify),
         new go.Binding("toSpot", "toSpot", go.Spot.parse).makeTwoWay(go.Spot.stringify),
         $(go.Shape, {fill: 'lightblue', strokeWidth: 2}),  // Link appearance
         $(go.Shape, {fill: 'lightblue', strokeWidth: 2, toArrow: 'Standard'})
       );
 
     // Attach event handlers
     this.diagram.addDiagramListener('LinkDrawn', (e) => {
       const link = e.subject;
       const fromNode = link.fromNode;
       const toNode = link.toNode;
 
       if (fromNode && toNode && fromNode instanceof go.Node && toNode instanceof go.Node) {
         graphLinksModel.addLinkData(link.data);
       }
     });
 
   }



 
   private createPalette() {
     const myPalette =
       $(go.Palette, 'myPaletteDiv',  // must name or refer to the DIV HTML element
         { // share the templates with the Palette
           model: new go.GraphLinksModel([  // specify the contents of the Palette
             {key: 'alpha', color: 'lightblue'},
             {key: 'Beta', color: 'orange'},
             {key: 'Gamma', color: 'lightgreen'},
             {key: 'Delta', color: 'pink'}
           ]),
           grid:
           $(go.Panel, "Grid",
               { gridCellSize: new go.Size(10, 10) },
               $(go.Shape, "LineH", { strokeDashArray: [1, 9] })
           )
         });
         
 
     // put all the nodes in column 1 of the palette with rectangles of equal width
     myPalette.layout = $(go.GridLayout,          // automatically lay out the palette  grid
       {wrappingColumn: 1, cellSize: new go.Size(1, 1)}); // no space between rows and columns
     // the node template is a simple rectangle
     myPalette.nodeTemplate = this.makeNodeTemplate();
   }

  private makeNodeTemplate() {
    return $(go.Node, "Auto",
      {
        locationSpot: go.Spot.Center,
        movable: true
      },
      new go.Binding("location", "loc", go.Point.parse).makeTwoWay(go.Point.stringify),
      $(go.Shape, 'RoundedRectangle', // This is the main object of the node
        {
          width: 80, height: 80, strokeWidth: 0, fill: 'lightblue'
        }),
        $(go.TextBlock, { margin: 10, textAlign: "center"} as go.TextBlock,
         { fromLinkable: false, toLinkable: false, editable: true },
         new go.Binding("text", "key")
       ),
      $(go.Panel, "Spot", // This panel holds the port
      { background: null },  // Ensure the panel has no background
        $(go.Shape, "Circle", // The port shape
          {
            alignment: go.Spot.Center, // Align the port
            portId: "", // Declare this shape to be a "port"
            fromSpot: go.Spot.AllSides, // Links go out from all sides
            toSpot: go.Spot.AllSides, // Links come in from all sides
            fromLinkable: true, toLinkable: true,
            cursor: "pointer", // Cursor indication for linking,
            width: 50, height: 50,  // Small size for the port
            fill: "transparent", stroke: "transparent"
          }),
        $(go.TextBlock, { margin: 5 },
          new go.Binding("text", "key"))
      )
    );
  }
  
 
   @HostListener('document:mousemove', ['$event'])
   onMouseMove(event: MouseEvent) {
     if (this.isResizing) {
       const containerRect = this.container.nativeElement.getBoundingClientRect();
       const newWidth = event.clientX - containerRect.left;
       
       // Ensure the width does not go below the minimum width
       this.firstColumnWidth = newWidth > this.minWidth ? newWidth : this.minWidth;
     }
   }
   @HostListener('document:mouseup', ['$event'])
   onMouseUp(event: MouseEvent) {
     if (this.isResizing) {
       this.isResizing = false;
     }
   }
 
   startResizing(event: MouseEvent) {
     event.preventDefault();
     this.isResizing = true;
   }




}
