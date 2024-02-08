import { Component } from '@angular/core';
import { Observable, switchMap, map, of } from 'rxjs';
import { Asset } from 'src/app/model/asset.class';
import { DataService } from 'src/app/services/data.service';
import { DiagramService } from 'src/app/services/diagram.service';

@Component({
  selector: 'app-node-form',
  templateUrl: './node-form.component.html',
  styleUrls: ['./node-form.component.scss']
})
export class NodeFormComponent {
  selectedNodeType: string | null = null;

  constructor(
    private dataService: DataService,
    private diagramService: DiagramService
  ) {}

  ngOnInit(): void {

    this.diagramService.selectedNode$.subscribe(node => {
      this.selectedNodeType = node?.data?.type || null;
    });
  }
}
