import {Component, ElementRef, AfterViewInit, OnInit} from '@angular/core';
import * as go from 'gojs';

@Component({
  selector: 'app-diagram',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {


  ngOnInit() {

    //write methods to create the gojs diagram
    this.createDiagram();

    //write a metohd to create the palette
    this.createPalette();
  }


  private createDiagram() {
    // create a diagram and palette with drag and drop from the palette to the diagram
    const $ = go.GraphObject.make;
    const myDiagram = $(go.Diagram, 'myDiagramDiv', // create a Diagram for the DIV HTML element
      { // enable undo & redo and a clipboard
        'undoManager.isEnabled': true,
        "linkingTool.isEnabled": true  // enable linking operations
      }); // end Diagram initialization

    // define a simple Node template
    myDiagram.nodeTemplate =  // the default node template
      $(go.Node, 'Auto',  // the Shape will go around the TextBlock
        $(go.Shape, 'RoundedRectangle', { strokeWidth: 0},  // stroke a width of zero
          // Shape.fill is bound to Node.data.color
          new go.Binding('fill', 'color')),  // end Shape
        $(go.TextBlock, { margin: 8 },  // some room around the text
          // TextBlock.text is bound to Node.data.key
          new go.Binding('text', 'key')));  // end TextBlock and go.Node


    // define the diagram model with nodeDataArray and linkDataArray
     const model: go.GraphLinksModel = new go.GraphLinksModel( [],[])  as go.GraphLinksModel
     let graphLinksModel = myDiagram.model as go.GraphLinksModel;


    myDiagram.linkTemplate =
      $(go.Link,
        { routing: go.Link.AvoidsNodes, curve: go.Link.JumpOver },
        $(go.Shape, { stroke: 'gray', strokeWidth: 1.5 }),  // Link appearance
        $(go.Shape, { toArrow: 'Standard', stroke: 'gray' })  // Arrowhead
      );


    // Attach event handlers
    myDiagram.addDiagramListener('LinkDrawn', (e) => {
      const link = e.subject;
      const fromNode = link.fromNode;
      const toNode = link.toNode;

      if (fromNode && toNode && fromNode instanceof go.Node && toNode instanceof go.Node) {
        // Perform any additional customizations on the link if needed
        // For example, you can modify the link's appearance or add data properties

        // Add the link to the diagram's model
        graphLinksModel.addLinkData(link.data);
      }
    });

  }

  private createPalette() {
    // create a palette with four nodes and allow the user to copy them (execute a function) by dragging from the palette to the diagram
    const $ = go.GraphObject.make;  // for conciseness in defining templates
    const myPalette =
      $(go.Palette, 'myPaletteDiv',  // must name or refer to the DIV HTML element
        { // share the templates with the Palette
          model: new go.GraphLinksModel([  // specify the contents of the Palette
            { key: 'Alpha', color: 'lightblue' },
            { key: 'Beta', color: 'orange' },
            { key: 'Gamma', color: 'lightgreen' },
            { key: 'Delta', color: 'pink' }
          ])
        });
    // put all the nodes in column 1 of the palette with rectangles of equal width
    myPalette.layout =  $(go.GridLayout,          // automatically lay out the palette  grid
      { wrappingColumn: 1, cellSize: new go.Size(1, 1) }); // no space between rows and columns
    // the node template is a simple rectangle
    myPalette.nodeTemplate =
      $(go.Node, 'Auto',  // the Shape will go around the TextBlock
        $(go.Shape, 'RoundedRectangle', { strokeWidth: 0},  // stroke a width of zero
          // Shape.fill is bound to Node.data.color
          new go.Binding('fill', 'color')),  // end Shape
        $(go.TextBlock, { margin: 8 },  // some room around the text
          // TextBlock.text is bound to Node.data.key
          new go.Binding('text', 'key')));  // end TextBlock and go.Node

  }
}
