import { ComponentFixture, TestBed } from '@angular/core/testing';

import { KubeRatioTextComponent } from './kube-ratio-text.component';

describe('KubeRatioTextComponent', () => {
  let component: KubeRatioTextComponent;
  let fixture: ComponentFixture<KubeRatioTextComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [KubeRatioTextComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(KubeRatioTextComponent);
    component = fixture.componentInstance;
  });

  it('renders a preformatted ratio string as text', () => {
    component.value = '0/1';
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('0/1');
  });

  it('renders numeric numerator/denominator as X/Y text', () => {
    component.numerator = 2;
    component.denominator = 5;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('2/5');
  });

  it('renders fallback when no value or numbers are provided', () => {
    component.fallback = '—';
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('—');
  });

  it('does not render a progress bar element', () => {
    component.value = '1/1';
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('p-progressbar')).toBeNull();
    expect(fixture.nativeElement.querySelector('p-progressBar')).toBeNull();
  });
});

