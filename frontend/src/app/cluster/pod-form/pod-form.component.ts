import { Component, Input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AutoSaveService } from '../../services/auto-save.service';
import { Pod, RestartPolicy, DNSPolicy, PreemptionPolicy } from '../../model/pod.class';

@Component({
  selector: 'app-pod-form',
  templateUrl: './pod-form.component.html',
  styleUrls: ['./pod-form.component.scss'],
  providers: [AutoSaveService]
})
export class PodFormComponent implements OnInit {
  @Input() selectedNode!: Pod;
  form!: FormGroup;
  restartPolicies: RestartPolicy[] = ['Always', 'OnFailure', 'Never'];
  dnsPolicies: DNSPolicy[] = ['ClusterFirst', 'ClusterFirstWithHostNet', 'Default', 'None'];
  preemptionPolicies: PreemptionPolicy[] = ['PreemptLowerPriority', 'Never'];

  constructor(
    private fb: FormBuilder,
    private autoSaveService: AutoSaveService
  ) {}

  ngOnInit() {
    this.initForm();
    this.setupAutoSave();
  }

  private initForm() {
    this.form = this.fb.group({
      name: [this.selectedNode.name || '', [Validators.required, Validators.pattern('[a-z0-9]([-a-z0-9]*[a-z0-9])?')]],
      restartPolicy: [this.selectedNode.restartPolicy || 'Always', Validators.required],
      serviceAccountName: [this.selectedNode.serviceAccountName || 'default'],
      hostNetwork: [this.selectedNode.hostNetwork || false],
      dnsPolicy: [this.selectedNode.dnsPolicy || 'ClusterFirst', Validators.required],
      schedulerName: [this.selectedNode.schedulerName || 'default-scheduler'],
      priority: [this.selectedNode.priority || 0, [Validators.min(0), Validators.max(1000000000)]],
      preemptionPolicy: [this.selectedNode.preemptionPolicy || 'PreemptLowerPriority']
    });
  }


  private setupAutoSave() {
    this.autoSaveService.enableAutoSave(this.form, this.selectedNode.id, this.form.valueChanges);
  }

}