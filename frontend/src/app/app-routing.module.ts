/*
 * IzyKube
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import { NgModule, isDevMode } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { ClusterListComponent } from './cluster/cluster-list/cluster-list.component';
import { AssetListComponent } from './assets/assets-list/assets-list.component';
import { ClusterEditorComponent } from './cluster/cluster-editor/cluster-editor.component';
import { ClusterFormComponent } from './cluster/cluster-form/cluster-form.component';
import { NamespaceVersionsComponent } from './cluster/namespace-versions/namespace-versions.component';
import { AssetFormComponent } from './assets/asset-form/asset-form.component';
import { KubeExplorerComponent } from './kube-explorer/kube-explorer.component';
import { RoutesComponent } from './routes/routes.component';
import { SettingsComponent } from './settings/settings.component';
import { PortForwardListComponent } from './port-forward/port-forward-list.component';
import { FormWorkbenchComponent } from './dev/form-workbench/form-workbench.component';
import { OperatorCatalogComponent } from './operator-catalog/operator-catalog.component';

const routes: Routes = [
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: 'cluster-editor', component: ClusterEditorComponent },
  { path: 'cluster-editor/:id', component: ClusterEditorComponent },
  { path: 'home', component: HomeComponent },
  { path: 'namespaces', component: ClusterListComponent },
  { path: 'namespaces/:namespace/versions', component: NamespaceVersionsComponent },
  { path: 'clusters', redirectTo: 'namespaces', pathMatch: 'full' },
  { path: 'assets', component: AssetListComponent },
  { path: 'cluster-form', component: ClusterFormComponent },
  { path: 'cluster-form/:id', component: ClusterFormComponent },
  { path: 'asset-form', component: AssetFormComponent },
  { path: 'asset-form/:id', component: AssetFormComponent },
  { path: 'kube-explorer', component: KubeExplorerComponent },
  { path: 'routes', component: RoutesComponent },
  { path: 'settings', component: SettingsComponent },
  { path: 'port-forwards', component: PortForwardListComponent },
  { path: 'operators', redirectTo: 'operator-catalog', pathMatch: 'full' },
  { path: 'operator-catalog', component: OperatorCatalogComponent }
];

if (isDevMode()) {
  routes.push({ path: 'dev/forms', component: FormWorkbenchComponent });
}

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
