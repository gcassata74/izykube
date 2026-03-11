import { MenubarModule } from 'primeng/menubar';
import { NgModule, isDevMode } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { MenuModule } from 'primeng/menu';
import { DropdownModule } from 'primeng/dropdown';
import { MultiSelectModule } from 'primeng/multiselect';
import { TableModule } from 'primeng/table';
import { SplitButtonModule } from 'primeng/splitbutton';
import { DialogModule } from 'primeng/dialog';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ProgressBarModule } from 'primeng/progressbar';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HomeComponent } from './home/home.component';
import { DiagramComponent } from './diagram/diagram.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import {ToolbarModule} from 'primeng/toolbar';
import { OverlayPanelModule } from 'primeng/overlaypanel';
import { StoreModule } from '@ngrx/store';
import { EffectsModule } from '@ngrx/effects';
import { StoreDevtoolsModule } from '@ngrx/store-devtools';
import { StoreRouterConnectingModule } from '@ngrx/router-store';
import {reducers} from './store/reducers';
import { ClusterListComponent } from './cluster/cluster-list/cluster-list.component';
import { NamespaceVersionsComponent } from './cluster/namespace-versions/namespace-versions.component';
import { FormsModule } from '@angular/forms';
import { ToolbarService } from './services/toolbar.service';
import { HttpClientModule } from '@angular/common/http';
import { DiagramService } from './services/diagram.service';
import { ClusterEditorComponent } from './cluster/cluster-editor/cluster-editor.component';
import { NodeFormComponent } from './cluster/node-form/node-form.component';
import { DeploymentFormComponent } from './cluster/deployment-form/deployment-form.component';
import { ReactiveFormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { CheckboxModule } from 'primeng/checkbox';
import { InputNumberModule } from 'primeng/inputnumber';
import { ContextMenuModule } from 'primeng/contextmenu';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ToastModule } from 'primeng/toast';
import { ConfigBundleFormComponent } from './cluster/config-bundle-form/config-bundle-form.component';
import { ClusterService } from './services/cluster.service';
import { ClusterFormComponent } from './cluster/cluster-form/cluster-form.component';
import { MessageService, ConfirmationService } from 'primeng/api';
import { CardModule } from 'primeng/card';
import { InputSwitchModule } from 'primeng/inputswitch';
import { ServiceFormComponent } from './cluster/service-form/service-form.component';
import { IngressFormComponent } from './cluster/ingress-form/ingress-form.component';
import { TabViewModule } from 'primeng/tabview';
import { ContainerFormComponent } from './cluster/container-form/container-form.component';
import { VolumeFormComponent } from './cluster/volume-form/volume-form.component';
import { AssetListComponent } from './assets/assets-list/assets-list.component';
import { AssetFormComponent } from './assets/asset-form/asset-form.component';
import { ClusterEffect } from './store/effects/effect';
import { EditorModule } from 'primeng/editor';
import { SharedModule } from './shared/shared.module';
import { JobFormComponent } from './cluster/job-form/job-form.component';
import { IstioFormComponent } from './cluster/istio-form/istio-form.component';
import { DragDropDirective } from './directives/drag-drop.directive';
import { KubeExplorerComponent } from './kube-explorer/kube-explorer.component';
import { KubeRatioTextComponent } from './kube-explorer/kube-ratio-text/kube-ratio-text.component';
import { KubeRowActionsComponent } from './kube-explorer/kube-row-actions/kube-row-actions.component';
import { RoutesComponent } from './routes/routes.component';
import { ResourceYamlDialogComponent } from './resource-yaml-dialog/resource-yaml-dialog.component';
import { PodShellDialogComponent } from './pod-shell-dialog/pod-shell-dialog.component';
import { HeaderComponent } from './layout/header/header.component';
import { SidebarComponent } from './layout/sidebar/sidebar.component';
import { TooltipModule } from 'primeng/tooltip';
import { SettingsComponent } from './settings/settings.component';
import { PersistentVolumeAdminComponent } from './settings/persistent-volume-admin/persistent-volume-admin.component';
import { PortForwardListComponent } from './port-forward/port-forward-list.component';
import { LinkPropertiesFormComponent } from './diagram/link-properties-form/link-properties-form.component';
import { FormWorkbenchComponent } from './dev/form-workbench/form-workbench.component';
import { ServiceAccountFormComponent } from './cluster/service-account-form/service-account-form.component';
import { AccessPolicyFormComponent } from './cluster/access-policy-form/access-policy-form.component';
import { OperatorCatalogComponent } from './operator-catalog/operator-catalog.component';
import { PaginatorModule } from 'primeng/paginator';
import { CustomResourceFormComponent } from './cluster/custom-resource-form/custom-resource-form.component';

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    DiagramComponent,
    ClusterListComponent,
    NamespaceVersionsComponent,
    AssetListComponent,
    ClusterEditorComponent,
    NodeFormComponent,
    DeploymentFormComponent,
    ConfigBundleFormComponent,
    ClusterFormComponent,
    ServiceFormComponent,
    IngressFormComponent,
    ContainerFormComponent,
    VolumeFormComponent,
    AssetFormComponent,
    JobFormComponent,
    IstioFormComponent,
    DragDropDirective,
    KubeExplorerComponent,
    RoutesComponent,
    KubeRatioTextComponent,
    KubeRowActionsComponent,
    ResourceYamlDialogComponent,
    PodShellDialogComponent,
    HeaderComponent,
    SidebarComponent,
    SettingsComponent,
    PersistentVolumeAdminComponent,
    PortForwardListComponent,
    LinkPropertiesFormComponent,
    FormWorkbenchComponent,
    ServiceAccountFormComponent,
    AccessPolicyFormComponent,
    OperatorCatalogComponent,
    CustomResourceFormComponent,
  ],
  imports: [
    SharedModule,
    ReactiveFormsModule,
    BrowserModule,
    AppRoutingModule,
    ToolbarModule,
    DropdownModule,
    MultiSelectModule,
    InputTextModule,
    TabViewModule,
    InputNumberModule,
    SplitButtonModule,
    DialogModule,
    InputTextareaModule,
    ProgressSpinnerModule,
    ProgressBarModule,
    ConfirmDialogModule,
    ContextMenuModule,
    CheckboxModule,
    EditorModule,
    ToastModule,
    MenuModule,
    CardModule,
    MenubarModule,
    ButtonModule,
    InputSwitchModule,
    TableModule,
    MenuModule,
    MessageModule,
    OverlayPanelModule,
    BrowserAnimationsModule,
    FormsModule,
    HttpClientModule,
    TooltipModule,
    PaginatorModule,
    StoreModule.forRoot(reducers),
    EffectsModule.forRoot([ClusterEffect]),
    StoreDevtoolsModule.instrument({ maxAge: 25, logOnly: !isDevMode() }),
    StoreRouterConnectingModule.forRoot(),
  ],
  providers: [
    ToolbarService,
    DiagramService,
    ClusterService,
    MessageService,
    ConfirmationService
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
