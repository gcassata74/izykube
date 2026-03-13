/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { map, skip } from 'rxjs/operators';
import { ServiceAccount } from 'src/app/model/service-account.class';
import { AutoSaveService } from 'src/app/services/auto-save.service';

@Component({
  selector: 'app-service-account-form',
  templateUrl: './service-account-form.component.html',
  providers: [AutoSaveService]
})
export class ServiceAccountFormComponent implements OnInit, OnChanges, OnDestroy {
  @Input() selectedNode!: ServiceAccount;
  @Input() clusterNamespace: string = 'default';

  form!: FormGroup;
  private autoSaveNodeId: string | null = null;
  readonly rbacProfiles = [
    { label: $localize`:@@common.none:None`, value: 'NONE' },
    { label: $localize`:@@serviceAccount.rbac.view:View (read-only)`, value: 'VIEW' },
    { label: $localize`:@@serviceAccount.rbac.edit:Edit (read/write)`, value: 'EDIT' },
    { label: $localize`:@@serviceAccount.rbac.admin:Admin`, value: 'ADMIN' }
  ];

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.setupAutoSave();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedNode'] && !changes['selectedNode'].firstChange && this.form) {
      this.refreshFormValues(changes['selectedNode'].currentValue as ServiceAccount);
      this.setupAutoSave();
    }
  }

  ngOnDestroy(): void {
    this.flushPendingChanges();
  }

  get labelsArray(): FormArray<FormGroup> {
    return this.form.get('labels') as FormArray<FormGroup>;
  }

  get annotationsArray(): FormArray<FormGroup> {
    return this.form.get('annotations') as FormArray<FormGroup>;
  }

  addLabel(): void {
    this.labelsArray.push(this.createKeyValueGroup());
  }

  removeLabel(index: number): void {
    this.labelsArray.removeAt(index);
  }

  addAnnotation(): void {
    this.annotationsArray.push(this.createKeyValueGroup());
  }

  removeAnnotation(index: number): void {
    this.annotationsArray.removeAt(index);
  }

  trackByIndex(index: number): number {
    return index;
  }

  get namespaceDisplayText(): string {
    const ns = this.clusterNamespace || 'default';
    return $localize`:@@serviceAccount.namespaceDisplay:Namespace: ${ns}:namespace:`;
  }

  private initForm(): void {
    const node = this.selectedNode as ServiceAccount;
    const namespace = (this.clusterNamespace || 'default').trim() || 'default';

    this.form = this.fb.group({
      name: [node?.name ?? '', Validators.required],
      automountServiceAccountToken: [node?.automountServiceAccountToken ?? true],
      rbacProfile: [String((node as any)?.rbacProfile ?? 'NONE').toUpperCase()],
      labels: this.fb.array(this.buildKeyValueGroups(node?.labels)),
      annotations: this.fb.array(this.buildKeyValueGroups(node?.annotations))
    });
  }

  private refreshFormValues(node: ServiceAccount): void {
    this.form.patchValue({
      name: node?.name ?? '',
      automountServiceAccountToken: node?.automountServiceAccountToken ?? true,
      rbacProfile: String((node as any)?.rbacProfile ?? 'NONE').toUpperCase()
    }, { emitEvent: false });

    this.form.setControl('labels', this.fb.array(this.buildKeyValueGroups(node?.labels)));
    this.form.setControl('annotations', this.fb.array(this.buildKeyValueGroups(node?.annotations)));
  }

  private setupAutoSave(): void {
    if (!this.form || !this.selectedNode || this.autoSaveNodeId === this.selectedNode.id) {
      return;
    }
    const payload$: Observable<any> = this.form.valueChanges.pipe(
      skip(1),
      map(() => this.buildNodeUpdatePayload())
    );
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, payload$);
    this.autoSaveNodeId = this.selectedNode.id;
  }

  private flushPendingChanges(): void {
    if (!this.form || !this.selectedNode?.id) {
      return;
    }
    this.autoSaveService.flushPendingChanges(this.selectedNode.id, this.buildNodeUpdatePayload());
  }

  private createKeyValueGroup(entry?: { key?: string; value?: string }): FormGroup {
    return this.fb.group({
      key: [entry?.key ?? ''],
      value: [entry?.value ?? '']
    });
  }

  private buildKeyValueGroups(record?: Record<string, string>): FormGroup[] {
    const entries = Object.entries(record ?? {});
    if (!entries.length) {
      return [];
    }
    return entries.map(([key, value]) => this.createKeyValueGroup({ key, value }));
  }

  private formArrayToRecord(array: FormArray<FormGroup>): Record<string, string> {
    const record: Record<string, string> = {};
    (array?.controls ?? []).forEach(group => {
      const key = String(group.get('key')?.value ?? '').trim();
      if (!key) {
        return;
      }
      record[key] = String(group.get('value')?.value ?? '');
    });
    return record;
  }

  private buildNodeUpdatePayload(): any {
    const namespace = (this.clusterNamespace || 'default').trim() || 'default';
    const rawProfile = String(this.form.get('rbacProfile')?.value ?? 'NONE').trim().toUpperCase();
    const rbacProfile = rawProfile === 'VIEW' || rawProfile === 'EDIT' || rawProfile === 'ADMIN' ? rawProfile : 'NONE';
    return {
      name: String(this.form.get('name')?.value ?? '').trim(),
      namespace,
      automountServiceAccountToken: !!this.form.get('automountServiceAccountToken')?.value,
      rbacProfile,
      labels: {},
      annotations: this.formArrayToRecord(this.annotationsArray)
    };
  }
}
