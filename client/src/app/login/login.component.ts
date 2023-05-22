import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import {AuthenticationService, Credentials} from '../services/authentication.service';
import {firstValueFrom} from "rxjs";

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {

  credentials: Credentials = {username: '', password: ''};
  loginForm: FormGroup = new FormGroup({});
  errorMessage = '';

  constructor(
    private formBuilder: FormBuilder,
    private router: Router,
    private authenticationService: AuthenticationService
  ) {}

  ngOnInit() {
    this.createForm();
  }

  createForm() {
    this.loginForm = this.formBuilder.group({
      username: ['', Validators.required],
      password: ['', Validators.required]
    });
  }

  async onSubmit() {
    this.credentials.username = this.loginForm.value.username;
    this.credentials.password = this.loginForm.value.password;

    try {
      const token = await firstValueFrom(this.authenticationService.authenticate(this.credentials));
      if(token){
        sessionStorage.setItem("jwt-token", token);
        this.router.navigate(['/home']);
      }
    } catch (error:any){
      this.errorMessage = error.message;
    }

  }
}
