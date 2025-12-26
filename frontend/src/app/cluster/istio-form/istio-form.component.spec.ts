import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IstioFormComponent } from './istio-form.component';
import { ReactiveFormsModule } from '@angular/forms';
import { DropdownModule } from 'primeng/dropdown';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Istio } from '../../model/istio.class';
import { AutoSaveService } from '../../services/auto-save.service';

describe('IstioFormComponent', () => {
  let component: IstioFormComponent;
  let fixture: ComponentFixture<IstioFormComponent>;

  beforeEach(async () => {
    const autoSaveStub = { enableAutoSave: () => {}, flushPendingChanges: () => {} };

    TestBed.configureTestingModule({
      imports: [
        ReactiveFormsModule,
        DropdownModule,
        InputNumberModule,
        InputTextModule,
        NoopAnimationsModule
      ],
      declarations: [IstioFormComponent],
      providers: []
    });

    TestBed.overrideComponent(IstioFormComponent, {
      set: {
        providers: [{ provide: AutoSaveService, useValue: autoSaveStub }],
      },
    });

    await TestBed.compileComponents();

    fixture = TestBed.createComponent(IstioFormComponent);
    component = fixture.componentInstance;
    component.selectedNode = new Istio('istio:test', 'istio', 'example.com', '/', '', 80);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
