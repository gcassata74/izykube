import {AfterViewInit, Component, ElementRef, OnInit, ViewChild} from '@angular/core';

@Component({
  selector: 'app-palette',
  templateUrl: './palette.component.html',
  styleUrls: ['./palette.component.scss']
})
export class PaletteComponent implements OnInit,AfterViewInit {

  @ViewChild('paletteDiv') paletteRef: ElementRef;
  palette: go.Palette;

  constructor() { }

  ngOnInit(): void {
  }

  ngAfterViewInit(): void {
  }

}
