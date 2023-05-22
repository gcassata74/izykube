import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import {ButtonModule} from 'primeng/button';
import {PanelMenuModule} from 'primeng/panelmenu';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {MegaMenuModule} from 'primeng/megamenu';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {FieldsetModule} from 'primeng/fieldset';
import {MessageModule} from 'primeng/message';
import {MessagesModule} from 'primeng/messages';
import {SidebarModule} from 'primeng/sidebar';
import {ToolbarModule} from 'primeng/toolbar';
import {HttpClientModule} from '@angular/common/http';
import {GojsAngularModule} from 'gojs-angular';
import { LoginComponent } from './login/login.component';
import {PasswordModule} from 'primeng/password';
import { HomeComponent } from './home/home.component';

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    HomeComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    BrowserModule,
    AppRoutingModule,
    ButtonModule,
    PanelMenuModule,
    MegaMenuModule,
    BrowserAnimationsModule,
    GojsAngularModule,
    FormsModule,
    FieldsetModule,
    ReactiveFormsModule,
    MessageModule,
    MessagesModule,
    SidebarModule,
    ToolbarModule,
    PasswordModule,
    HttpClientModule
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
