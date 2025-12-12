import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { TabViewModule } from 'primeng/tabview';
import { InputTextModule } from 'primeng/inputtext';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { DropdownModule } from 'primeng/dropdown';
import { InputSwitchModule } from 'primeng/inputswitch';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';

import { ConfigBundleFormComponent } from './config-bundle-form.component';
import { AutoSaveService } from '../../services/auto-save.service';
import { NotificationService } from '../../services/notification.service';
import { ensureConfigBundleDefaults } from '../../model/config-bundle.model';

class AutoSaveStub {
  enableAutoSave() {}
  flushPendingChanges() {}
}

class NotificationStub {
  success() {}
  warn() {}
  error() {}
}

describe('ConfigBundleFormComponent', () => {
  let component: ConfigBundleFormComponent;
  let fixture: ComponentFixture<ConfigBundleFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        BrowserAnimationsModule,
        ReactiveFormsModule,
        FormsModule,
        TabViewModule,
        InputTextModule,
        InputTextareaModule,
        DropdownModule,
        InputSwitchModule,
        DialogModule,
        ButtonModule
      ],
      declarations: [ConfigBundleFormComponent],
      providers: [
        { provide: AutoSaveService, useClass: AutoSaveStub },
        { provide: NotificationService, useClass: NotificationStub }
      ]
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigBundleFormComponent);
    component = fixture.componentInstance;
    component.selectedNode = {
      id: 'bundle-1',
      name: 'app-config',
      kind: 'configmap',
      isAffected: false,
      configBundle: ensureConfigBundleDefaults({
        id: 'bundle-1',
        name: 'app-config',
        namespace: 'default',
        annotations: {},
        entries: [],
        showSecretsAsPlain: false
      })
    } as any;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should add entries to the form', () => {
    const entriesBefore = component.entriesArray.length;
    component.addEntry();
    expect(component.entriesArray.length).toBe(entriesBefore + 1);
  });

  it('should update YAML preview when entries change', () => {
    const entry = component.entriesArray.at(0)!;
    entry.patchValue({
      key: 'APP_MODE',
      value: 'prod',
      sensitivity: 'PLAIN'
    });
    component['refreshYamlPreview']();
    expect(component.yamlPreview).toContain('ConfigMap');
    expect(component.yamlPreview).toContain('APP_MODE');
  });

  it('should apply entries from paste buffer', () => {
    component.openPasteDialog();
    component.pasteBuffer = 'USER=demo\nPASSWORD=safe';
    component.applyPasteBuffer();
    expect(component.entryControls.length).toBeGreaterThan(2);
  });

  it('should persist entries inside configBundle payload', () => {
    const entry = component.entriesArray.at(0)!;
    entry.patchValue({ key: 'API_URL', value: 'https://api.local', sensitivity: 'PLAIN' });

    const payload = (component as any).buildNodeUpdatePayload(component.selectedNode);
    expect(payload.configBundle.entries[0]).toEqual({
      key: 'API_URL',
      value: 'https://api.local',
      sensitivity: 'PLAIN'
    });

    const updatedNode = { ...(component.selectedNode as any), ...payload };
    const rebuiltBundle = (component as any).resolveBundleFromNode(updatedNode);
    expect(rebuiltBundle.entries[0].value).toBe('https://api.local');
  });
});
