import { MenubarModule } from 'primeng/menubar';
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
import { HttpClientModule } from '@angular/common/http';
import { DiagramService } from './services/diagram.service';
import { ClusterEditorComponent } from './cluster/cluster-editor/cluster-editor.component';
import { NodeFormComponent } from './cluster/node-form/node-form.component';
import { PodFormComponent } from './cluster/pod-form/pod-form.component';
import { DeploymentFormComponent } from './cluster/deployment-form/deployment-form.component';
import { ReactiveFormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { ContextMenuModule } from 'primeng/contextmenu';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ToastModule } from 'primeng/toast';
import { ConfigMapFormComponent } from './cluster/config-map-form/config-map-form.component';
import { ClusterService } from './services/cluster.service';
import { AutoSaveService } from './services/auto-save.service';
import { ClusterFormComponent } from './cluster/cluster-form/cluster-form.component';
import { MessageService } from 'primeng/api';


@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    DiagramComponent,
    ClusterListComponent,
    AssetsListComponent,
    ClusterEditorComponent,
    NodeFormComponent,
    PodFormComponent,
    DeploymentFormComponent,
    ConfigMapFormComponent,
    ClusterFormComponent,
  ],
  imports: [
    ReactiveFormsModule,
    BrowserModule,
    AppRoutingModule,
    SidebarModule,
    ToolbarModule,
    DropdownModule,
    InputTextModule,
    InputNumberModule,
    ContextMenuModule,
    ToastModule,
    MenuModule,
    MenubarModule,
    ButtonModule,
    TableModule,
    MenuModule,
    MessageModule,
    BrowserAnimationsModule,
    FormsModule,
    ReactiveFormsModule,
    HttpClientModule,
    StoreModule.forRoot(reducers),
    EffectsModule.forRoot([InitEffect]),
    StoreDevtoolsModule.instrument({ maxAge: 25, logOnly: !isDevMode() }),
    StoreRouterConnectingModule.forRoot()
  ],
  providers: [
    ToolbarService,
    DiagramService,
    ClusterService,
    MessageService
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
