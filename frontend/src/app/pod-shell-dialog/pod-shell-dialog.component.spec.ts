import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DialogModule } from 'primeng/dialog';
import { PodShellDialogComponent } from './pod-shell-dialog.component';
import { PodShellService } from '../services/pod-shell.service';

class MockPodShellService {
  createShellSocket(): WebSocket {
    return {
      readyState: WebSocket.CLOSED,
      send: () => {},
      close: () => {}
    } as unknown as WebSocket;
  }
}

describe('PodShellDialogComponent', () => {
  let component: PodShellDialogComponent;
  let fixture: ComponentFixture<PodShellDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DialogModule],
      declarations: [PodShellDialogComponent],
      providers: [{ provide: PodShellService, useClass: MockPodShellService }]
    }).compileComponents();

    fixture = TestBed.createComponent(PodShellDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should emit closed when dialog hides', () => {
    const closedSpy = spyOn(component.closed, 'emit');
    const visibleSpy = spyOn(component.visibleChange, 'emit');
    component.onDialogVisibleChange(false);
    expect(visibleSpy).toHaveBeenCalledWith(false);
    expect(closedSpy).toHaveBeenCalled();
  });
});
