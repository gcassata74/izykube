import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { DropdownModule } from 'primeng/dropdown';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { IngressFormComponent } from './ingress-form.component';
import { Ingress } from '../../model/ingress.class';
import { DiagramService } from '../../services/diagram.service';

describe('IngressFormComponent', () => {
  let component: IngressFormComponent;
  let fixture: ComponentFixture<IngressFormComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        ReactiveFormsModule,
        DropdownModule,
        InputNumberModule,
        InputTextModule,
        ButtonModule,
        NoopAnimationsModule
      ],
      declarations: [IngressFormComponent],
      providers: [
        { provide: DiagramService, useValue: { updateClusterNodes: jasmine.createSpy('updateClusterNodes') } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(IngressFormComponent);
    component = fixture.componentInstance;
    component.selectedNode = new Ingress('ingress:test', 'test', 'example.com', '/', '', 80);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('initializes tls and annotation controls', () => {
    expect(component.ingressForm.get('tls')).toBeTruthy();
    expect(component.annotationsArray.length).toBeGreaterThan(0);
  });

  it('adds annotations rows on demand', () => {
    const initial = component.annotationsArray.length;
    component.addAnnotation({ key: 'nginx.ingress.kubernetes.io/ssl-redirect', value: 'true' });
    expect(component.annotationsArray.length).toBe(initial + 1);
  });
});
