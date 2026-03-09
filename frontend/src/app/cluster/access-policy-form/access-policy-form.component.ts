import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { map, skip } from 'rxjs/operators';
import {
  AccessPolicy,
  AccessPolicyBindingKind,
  AccessPolicyBindingStrategy,
  AccessPolicyNodeType,
  AccessPolicyRoleKind,
  AccessPolicyRule
} from 'src/app/model/access-policy.class';
import { AutoSaveService } from 'src/app/services/auto-save.service';
import { Node } from 'src/app/model/node.class';

@Component({
  selector: 'app-access-policy-form',
  templateUrl: './access-policy-form.component.html',
  providers: [AutoSaveService]
})
export class AccessPolicyFormComponent implements OnInit, OnChanges, OnDestroy {
  @Input() selectedNode!: AccessPolicy;
  @Input() clusterNamespace: string = 'default';
  @Input() clusterNodes: Node[] = [];

  form!: FormGroup;
  private autoSaveNodeId: string | null = null;

  readonly strategyOptions: { label: string; value: AccessPolicyBindingStrategy }[] = [
    { label: 'Workload ServiceAccount per workload', value: 'WORKLOAD_SA_PER_WORKLOAD' },
    { label: 'Workload ServiceAccount per policy', value: 'WORKLOAD_SA_PER_POLICY' },
    { label: 'Use existing ServiceAccount name (advanced)', value: 'WORKLOAD_SA_EXPLICIT_REFERENCE' }
  ];
  readonly roleKindOptions: { label: string; value: AccessPolicyRoleKind }[] = [
    { label: 'Role (namespaced)', value: 'Role' },
    { label: 'ClusterRole (cluster-scoped)', value: 'ClusterRole' }
  ];
  readonly bindingKindOptions: { label: string; value: AccessPolicyBindingKind }[] = [
    { label: 'RoleBinding (namespaced)', value: 'RoleBinding' },
    { label: 'ClusterRoleBinding (cluster-scoped)', value: 'ClusterRoleBinding' }
  ];
  readonly roleRefKindOptions: { label: string; value: AccessPolicyRoleKind }[] = [
    { label: 'Role', value: 'Role' },
    { label: 'ClusterRole', value: 'ClusterRole' }
  ];

  readonly verbOptions: { label: string; value: string }[] = [
    { label: 'get', value: 'get' },
    { label: 'list', value: 'list' },
    { label: 'watch', value: 'watch' },
    { label: 'create', value: 'create' },
    { label: 'update', value: 'update' },
    { label: 'patch', value: 'patch' },
    { label: 'delete', value: 'delete' },
    { label: 'deletecollection', value: 'deletecollection' }
  ];

  readonly apiGroupOptions: { label: string; value: string }[] = [
    { label: '(core)', value: '' },
    { label: 'apps', value: 'apps' },
    { label: 'batch', value: 'batch' },
    { label: 'networking.k8s.io', value: 'networking.k8s.io' },
    { label: 'policy', value: 'policy' },
    { label: 'rbac.authorization.k8s.io', value: 'rbac.authorization.k8s.io' },
    { label: 'autoscaling', value: 'autoscaling' },
    { label: 'coordination.k8s.io', value: 'coordination.k8s.io' },
    { label: 'discovery.k8s.io', value: 'discovery.k8s.io' },
    { label: 'events.k8s.io', value: 'events.k8s.io' }
  ];

  readonly resourceOptions: { label: string; value: string }[] = [
    { label: 'pods', value: 'pods' },
    { label: 'pods/log', value: 'pods/log' },
    { label: 'pods/exec', value: 'pods/exec' },
    { label: 'services', value: 'services' },
    { label: 'endpoints', value: 'endpoints' },
    { label: 'configmaps', value: 'configmaps' },
    { label: 'secrets', value: 'secrets' },
    { label: 'serviceaccounts', value: 'serviceaccounts' },
    { label: 'deployments', value: 'deployments' },
    { label: 'replicasets', value: 'replicasets' },
    { label: 'statefulsets', value: 'statefulsets' },
    { label: 'daemonsets', value: 'daemonsets' },
    { label: 'jobs', value: 'jobs' },
    { label: 'cronjobs', value: 'cronjobs' },
    { label: 'virtualservices', value: 'virtualservices' },
    { label: 'gateways', value: 'gateways' },
    { label: 'networkpolicies', value: 'networkpolicies' },
    { label: 'persistentvolumeclaims', value: 'persistentvolumeclaims' },
    { label: 'events', value: 'events' }
  ];

  get resourceNameOptions(): { label: string; value: string }[] {
    const names = (this.clusterNodes ?? [])
      .map(node => String((node as any)?.name ?? '').trim())
      .filter(Boolean);
    const unique = Array.from(new Set(names)).sort((a, b) => a.localeCompare(b));
    return unique.map(name => ({ label: name, value: name }));
  }

  get serviceAccountNameOptions(): { label: string; value: string }[] {
    const names = (this.clusterNodes ?? [])
      .filter(node => String((node as any)?.kind ?? '').toLowerCase() === 'serviceaccount')
      .map(node => String((node as any)?.name ?? '').trim())
      .filter(Boolean);
    const unique = Array.from(new Set(names)).sort((a, b) => a.localeCompare(b));
    return unique.map(name => ({ label: name, value: name }));
  }

  get roleNameOptions(): { label: string; value: string }[] {
    const names = (this.clusterNodes ?? [])
      .filter(node =>
        String((node as any)?.kind ?? '').toLowerCase() === 'accesspolicy'
        && String((node as any)?.rbacNodeType ?? 'ROLE').toUpperCase() !== 'ROLEBINDING'
      )
      .map(node => String((node as any)?.name ?? '').trim())
      .filter(Boolean);
    const unique = Array.from(new Set(names)).sort((a, b) => a.localeCompare(b));
    return unique.map(name => ({ label: name, value: name }));
  }

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
      this.refreshFormValues(changes['selectedNode'].currentValue as AccessPolicy);
      this.setupAutoSave();
    }
  }

  ngOnDestroy(): void {
    this.flushPendingChanges();
  }

  get rulesArray(): FormArray<FormGroup> {
    return this.form.get('rules') as FormArray<FormGroup>;
  }

  addRule(): void {
    this.rulesArray.push(this.createRuleGroup());
  }

  removeRule(index: number): void {
    this.rulesArray.removeAt(index);
  }

  trackByIndex(index: number): number {
    return index;
  }

  get usesExplicitServiceAccount(): boolean {
    return this.form?.get('targetBindingStrategy')?.value === 'WORKLOAD_SA_EXPLICIT_REFERENCE';
  }

  get isRoleBindingNode(): boolean {
    return this.form?.get('rbacNodeType')?.value === 'ROLEBINDING';
  }

  get isRoleNode(): boolean {
    return !this.isRoleBindingNode;
  }

  private initForm(): void {
    const node = this.selectedNode as AccessPolicy;
    const defaultNamespace = (node?.namespace || this.clusterNamespace || 'default').trim() || 'default';

    this.form = this.fb.group({
      name: [node?.name ?? '', Validators.required],
      namespace: [defaultNamespace],
      rbacNodeType: [node?.rbacNodeType ?? 'ROLE'],
      roleKind: [node?.roleKind ?? 'Role', Validators.required],
      bindingKind: [node?.bindingKind ?? 'RoleBinding', Validators.required],
      roleRefName: [node?.roleRefName ?? ''],
      roleRefKind: [node?.roleRefKind ?? 'Role'],
      subjectServiceAccountName: [node?.subjectServiceAccountName ?? ''],
      targetBindingStrategy: [node?.targetBindingStrategy ?? 'WORKLOAD_SA_PER_WORKLOAD', Validators.required],
      existingServiceAccountName: [node?.existingServiceAccountName ?? ''],
      rules: this.fb.array((node?.rules ?? []).map(rule => this.createRuleGroup(rule)))
    });

    if (this.rulesArray.length === 0) {
      this.addRule();
    }
  }

  private refreshFormValues(node: AccessPolicy): void {
    const defaultNamespace = (node?.namespace || this.clusterNamespace || 'default').trim() || 'default';
    this.form.patchValue({
      name: node?.name ?? '',
      namespace: defaultNamespace,
      rbacNodeType: node?.rbacNodeType ?? 'ROLE',
      roleKind: node?.roleKind ?? 'Role',
      bindingKind: node?.bindingKind ?? 'RoleBinding',
      roleRefName: node?.roleRefName ?? '',
      roleRefKind: node?.roleRefKind ?? 'Role',
      subjectServiceAccountName: node?.subjectServiceAccountName ?? '',
      targetBindingStrategy: node?.targetBindingStrategy ?? 'WORKLOAD_SA_PER_WORKLOAD',
      existingServiceAccountName: node?.existingServiceAccountName ?? ''
    }, { emitEvent: false });
    this.form.setControl('rules', this.fb.array((node?.rules ?? []).map(rule => this.createRuleGroup(rule))));
    if ((this.form.get('rules') as FormArray).length === 0) {
      this.addRule();
    }
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

  private createRuleGroup(rule?: Partial<AccessPolicyRule>): FormGroup {
    return this.fb.group({
      apiGroups: [this.normalizeStringArray((rule as any)?.apiGroups, ['']), Validators.required],
      resources: [this.normalizeStringArray((rule as any)?.resources, []), Validators.required],
      verbs: [this.normalizeStringArray((rule as any)?.verbs, []), Validators.required],
      resourceNames: [this.normalizeStringArray((rule as any)?.resourceNames, [])]
    });
  }

  private normalizeStringArray(value: any, fallback: string[]): string[] {
    if (Array.isArray(value)) {
      return value
        .map(v => String(v ?? '').trim())
        .filter(v => v.length > 0);
    }
    const raw = String(value ?? '').trim();
    if (!raw) {
      return fallback;
    }
    return raw.split(',').map(part => part.trim()).filter(Boolean);
  }

  private buildNodeUpdatePayload(): any {
    const namespace = String(this.form.get('namespace')?.value ?? this.clusterNamespace ?? 'default').trim() || 'default';
    const rbacNodeType = this.form.get('rbacNodeType')?.value as AccessPolicyNodeType;
    const roleKind = this.form.get('roleKind')?.value as AccessPolicyRoleKind;
    const bindingKind = this.form.get('bindingKind')?.value as AccessPolicyBindingKind;
    const roleRefName = String(this.form.get('roleRefName')?.value ?? '').trim();
    const roleRefKind = this.form.get('roleRefKind')?.value as AccessPolicyRoleKind;
    const subjectServiceAccountName = String(this.form.get('subjectServiceAccountName')?.value ?? '').trim();
    const targetBindingStrategy = this.form.get('targetBindingStrategy')?.value as AccessPolicyBindingStrategy;
    const existingServiceAccountName = String(this.form.get('existingServiceAccountName')?.value ?? '').trim();

    const rules: AccessPolicyRule[] = (this.rulesArray?.controls ?? [])
      .map(group => {
        const apiGroups = this.normalizeStringArray(group.get('apiGroups')?.value, ['']);
        const resources = this.normalizeStringArray(group.get('resources')?.value, []);
        const verbs = this.normalizeStringArray(group.get('verbs')?.value, []);
        const resourceNames = this.normalizeStringArray(group.get('resourceNames')?.value, []);
        return {
          apiGroups: apiGroups.length ? apiGroups : [''],
          resources,
          verbs,
          ...(resourceNames.length ? { resourceNames } : {})
        };
      })
      .filter(rule => rule.resources.length > 0 || rule.verbs.length > 0);

    return {
      name: String(this.form.get('name')?.value ?? '').trim(),
      namespace,
      rbacNodeType: rbacNodeType === 'ROLEBINDING' ? 'ROLEBINDING' : 'ROLE',
      roleKind: roleKind === 'ClusterRole' ? 'ClusterRole' : 'Role',
      bindingKind: rbacNodeType === 'ROLEBINDING'
        ? 'RoleBinding'
        : (bindingKind === 'ClusterRoleBinding' ? 'ClusterRoleBinding' : 'RoleBinding'),
      roleRefName: rbacNodeType === 'ROLEBINDING' ? null : (roleRefName || null),
      roleRefKind: rbacNodeType === 'ROLEBINDING' ? 'Role' : (roleRefKind === 'ClusterRole' ? 'ClusterRole' : 'Role'),
      subjectServiceAccountName: rbacNodeType === 'ROLEBINDING' ? null : (subjectServiceAccountName || null),
      targetBindingStrategy,
      ...(targetBindingStrategy === 'WORKLOAD_SA_EXPLICIT_REFERENCE'
        ? { existingServiceAccountName }
        : { existingServiceAccountName: null }),
      rules: rbacNodeType === 'ROLEBINDING' ? [] : rules
    };
  }
}
