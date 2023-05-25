import {Component, ElementRef, AfterViewInit, OnInit} from '@angular/core';
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
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {

  diagram!: go.Diagram;

  ngOnInit() {

    //write methods to create the gojs diagram
    this.createDiagram();

    //write a metohd to create the palette
    this.createPalette();
  }


  private createDiagram() {

    this.diagram = $(go.Diagram, 'myDiagramDiv', // create a Diagram for the DIV HTML element
      { // enable undo & redo and a clipboard
        'undoManager.isEnabled': true,
        "linkingTool.isEnabled": true,
        "relinkingTool.isEnabled": true
      }); // end Diagram initialization

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
          adjusting: go.Link.Stretch
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
            {key: 'Delta', color: 'pink', geo: 'F M0 0 L80 0 B-90 90 80 20 20 20 L100 100 20 100 B90 90 20 80 20 20z'}
          ])
        });
    // put all the nodes in column 1 of the palette with rectangles of equal width
    myPalette.layout = $(go.GridLayout,          // automatically lay out the palette  grid
      {wrappingColumn: 1, cellSize: new go.Size(1, 1)}); // no space between rows and columns
    // the node template is a simple rectangle
    myPalette.nodeTemplate = this.makeNodeTemplate();
  }

  private makeNodeTemplate() {

    const nodeTemplate = $(go.Node, 'Auto',  // the Shape will go around the TextBlock
      new go.Binding("location", "loc", go.Point.parse).makeTwoWay(go.Point.stringify),
      {
        fromSpot: go.Spot.AllSides, toSpot: go.Spot.AllSides,
        fromLinkable: true, toLinkable: true,
        locationSpot: go.Spot.Center
      },
      $(go.Shape, 'RoundedRectangle',
        {
          width: 80,  // Set the desired width of the node
          height: 80,  // Set the desired height of the node
          strokeWidth: 0,
          fill: 'lightblue'
        }),
      $(go.TextBlock, { margin: 10, textAlign: "center"} as go.TextBlock,
        { fromLinkable: false, toLinkable: false },
        new go.Binding("text", "key")
      ),
      this.makeLinkSelectionAdornment()
    );
    return nodeTemplate;
  }

  private makeLinkSelectionAdornment() {
    return {
      selectionAdornmentTemplate:
        $(go.Adornment,
          $(go.Shape, {
            isPanelMain: true,
            stroke: '#057D9F',
            strokeWidth: 3
          })
        )
    };
  }

}
