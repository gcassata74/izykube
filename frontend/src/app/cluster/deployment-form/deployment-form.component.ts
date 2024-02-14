import { Component } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { SelectItem } from 'primeng/api/selectitem';

@Component({
  selector: 'app-deployment-form',
  templateUrl: './deployment-form.component.html',
  styleUrls: ['./deployment-form.component.scss']
})
export class DeploymentFormComponent {
  deploymentForm!: FormGroup;
  strategies!: SelectItem[];

  constructor(private fb: FormBuilder) {}

  ngOnInit(): void {
    this.strategies = [
      { label: 'Rolling Update', value: 'rollingUpdate' },
      // ... other strategies
    ];

    this.deploymentForm = this.fb.group({
      replicas: [1, Validators.required],
      strategy: ['', Validators.required],
    });
  }

  saveDeployment(): void {
    if (this.deploymentForm.valid) {
      console.log('Deployment saved', this.deploymentForm.value);
      // Here you would typically dispatch an action or call a service to save the form values
    }
  }
  

}
