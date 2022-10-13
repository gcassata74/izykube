import {AfterViewInit, ChangeDetectorRef, Component, ElementRef, OnInit, ViewChild, ViewEncapsulation} from '@angular/core';
import * as go from 'gojs';

const $ = go.GraphObject.make;

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
  encapsulation: ViewEncapsulation.ShadowDom
})
export class HomeComponent implements OnInit, AfterViewInit {
  themeFontFamily!: 'Roboto, Helvetica Neue, sans-serif';

  public paletteDivClassName = 'myPaletteDiv';
  public state = {
    // Palette state props
    paletteNodeData: [
      { name: 'Epsilon', icon:'gateway.svg' },
      { name: 'Test', icon:'gateway.svg' }
    ],
    paletteModelData: { prop: 'val' }
  };

  constructor(private cdr: ChangeDetectorRef) {
  }

  ngOnInit(): void {
  }

  ngAfterViewInit() {
    this.cdr.detectChanges();
  }

  public initPalette(): go.Palette {

    const palette = $(go.Palette,  { // customize the GridLayout to align the centers of the locationObjects
      layout: $(go.GridLayout, {
        alignment: go.GridLayout.Location,
        spacing: new go.Size(0, 10),
        wrappingColumn: 1
      }),
      maxSelectionCount: 1,
      allowGroup: false,
      allowHorizontalScroll: false,
      'animationManager.isEnabled': false,
      'animationManager.isInitial': false
    });

    // define the Node template
    palette.nodeTemplate = this.makeNodeTemplate();
    return palette;
  }



  private makeNodeIcon() {
    return $(go.Panel, 'Viewbox', {
        width: 60,
        height: 60,
        padding: new go.Margin(5, 2, 5, 2),
        background: 'transparent',
        defaultAlignment: go.Spot.Center
      },
      $(go.Picture, {
          desiredSize: new go.Size(50, 50),
          imageAlignment: go.Spot.Center,
          imageStretch: go.GraphObject.Uniform
        },
        new go.Binding('source', 'icon', this.getIconUrl.bind(this))
      )
    );
  }

  private getIconUrl(){
    return 'assets/images/diagram/gateway.svg';
  }

  private makeNodeTitle() {
    return $(go.TextBlock, {
        font: '13px ' + this.themeFontFamily,
        verticalAlignment: go.Spot.Top,
        overflow: go.TextBlock.OverflowClip,
        isMultiline: true,
        width: 85,
        margin: new go.Margin(0, 0, 5, 0)
      },
      new go.Binding('text', 'name'));
  }

  private makeNodeSelectionAdornment() {
    return {
      selectionAdornmentTemplate:
        $(go.Adornment, 'Auto',
          $(go.Shape, 'Rectangle', {
            stroke: '#025066',
            strokeWidth: 3,
            fill: null
          }),
          $(go.Placeholder)
        )
    };
  }


  private makeNodeTemplate() {
    return $(go.Node, 'Vertical',
        {cursor: 'pointer', locationSpot: go.Spot.Center},
        this.makeNodeIcon(),
        this.makeNodeTitle(),
        this.makeNodeSelectionAdornment()
      );
  }
}
