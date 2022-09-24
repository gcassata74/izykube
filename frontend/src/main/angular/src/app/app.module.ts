import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import {ButtonModule} from 'primeng/button';
import {PanelMenuModule} from 'primeng/panelmenu';
import {MainComponent} from './main/main.component';
import {HomeComponent} from './home/home.component';
import {SidemenuComponent} from './sidemenu/sidemenu.component';
import {MegaMenu, MegaMenuModule} from 'primeng/megamenu';

@NgModule({
  declarations: [
    AppComponent,
    MainComponent,
    HomeComponent,
    SidemenuComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    ButtonModule,
    PanelMenuModule,
    MegaMenuModule
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
