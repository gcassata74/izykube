import { MenubarModule } from 'primeng/menubar';
import { NgModule, isDevMode } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { SidebarModule } from 'primeng/sidebar';
import { MenuModule } from 'primeng/menu';
import { DropdownModule } from 'primeng/dropdown';
import { TableModule } from 'primeng/table';
import { SplitButtonModule } from 'primeng/splitbutton';
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
import { ClusterListComponent } from './cluster/cluster-list/cluster-list.component';
import { FormsModule } from '@angular/forms';
import { ToolbarService } from './services/toolbar.service';
import { HttpClientModule } from '@angular/common/http';
import { DiagramService } from './services/diagram.service';
import { ClusterEditorComponent } from './cluster/cluster-editor/cluster-editor.component';
import { NodeFormComponent } from './cluster/node-form/node-form.component';
import { PodFormComponent } from './cluster/pod-form/pod-form.component';
import { DeploymentFormComponent } from './cluster/deployment-form/deployment-form.component';
import { ReactiveFormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { CheckboxModule } from 'primeng/checkbox';
import { InputNumberModule } from 'primeng/inputnumber';
import { ContextMenuModule } from 'primeng/contextmenu';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ToastModule } from 'primeng/toast';
import { ConfigMapFormComponent } from './cluster/config-map-form/config-map-form.component';
import { ClusterService } from './services/cluster.service';
import { ClusterFormComponent } from './cluster/cluster-form/cluster-form.component';
import { MessageService } from 'primeng/api';
import { CardModule } from 'primeng/card';
import { ServiceFormComponent } from './cluster/service-form/service-form.component';
import { IngressFormComponent } from './cluster/ingress-form/ingress-form.component';
import { TabViewModule } from 'primeng/tabview';
import { ContainerFormComponent } from './cluster/container-form/container-form.component';
import { VolumeFormComponent } from './cluster/volume-form/volume-form.component';
import { AssetListComponent } from './assets/assets-list/assets-list.component';
import { AssetFormComponent } from './assets/asset-form/asset-form.component';
import { ClusterEffect } from './store/effects/effect';

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    DiagramComponent,
    ClusterListComponent,
    AssetListComponent,
    ClusterEditorComponent,
    NodeFormComponent,
    PodFormComponent,
    DeploymentFormComponent,
    ConfigMapFormComponent,
    ClusterFormComponent,
    ServiceFormComponent,
    IngressFormComponent,
    ContainerFormComponent,
    VolumeFormComponent,
    AssetFormComponent,
  ],
  imports: [
    ReactiveFormsModule,
    BrowserModule,
    AppRoutingModule,
    SidebarModule,
    ToolbarModule,
    DropdownModule,
    InputTextModule,
    TabViewModule,
    InputNumberModule,
    SplitButtonModule,
    ContextMenuModule,
    CheckboxModule,
    ToastModule,
    MenuModule,
    CardModule,
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
    EffectsModule.forRoot([ClusterEffect]),
    StoreDevtoolsModule.instrument({ maxAge: 25, logOnly: !isDevMode() }),
    StoreRouterConnectingModule.forRoot(),
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
