import { NgModule, isDevMode } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { SidebarModule } from 'primeng/sidebar';
import { MenuModule } from 'primeng/menu';
import { DropdownModule } from 'primeng/dropdown';
import { TableModule } from 'primeng/table';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HomeComponent } from './home/home.component';
import { DiagramComponent } from './diagram/diagram.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import {ToolbarModule} from 'primeng/toolbar';
import { StoreModule } from '@ngrx/store';
import { EffectsModule } from '@ngrx/effects';
import { StoreDevtoolsModule } from '@ngrx/store-devtools';
import { StoreRouterConnectingModule } from '@ngrx/router-store';
import {reducers} from './store/reducers';
import {initialState} from './store/states/state';
import { ClusterListComponent } from './cluster/cluster-list/cluster-list.component';
import { FormsModule } from '@angular/forms';
import { AssetsListComponent } from './assets/assets-list/assets-list.component';
import { ToolbarService } from './services/toolbar.service';
import { InitEffect } from './store/effects/init-effects';
import { AssetSelectComponent } from './assets/asset-select/asset-select.component';
import { HttpClientModule } from '@angular/common/http';
import { DiagramService } from './services/diagram.service';

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    DiagramComponent,
    ClusterListComponent,
    AssetsListComponent,
    AssetSelectComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    SidebarModule,
    ToolbarModule,
    DropdownModule,
    TableModule,
    MenuModule,
    BrowserAnimationsModule,
    FormsModule,
    HttpClientModule,
    StoreModule.forRoot(reducers, {initialState}),
    EffectsModule.forRoot([InitEffect]),
    StoreDevtoolsModule.instrument({ maxAge: 25, logOnly: !isDevMode() }),
    StoreRouterConnectingModule.forRoot()
  ],
  providers: [
    ToolbarService,
    DiagramService
    
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
