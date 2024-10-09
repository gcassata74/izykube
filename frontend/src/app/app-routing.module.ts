import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { ClusterListComponent } from './cluster/cluster-list/cluster-list.component';
import { AssetListComponent } from './assets/assets-list/assets-list.component';
import { ClusterEditorComponent } from './cluster/cluster-editor/cluster-editor.component';
import { ClusterFormComponent } from './cluster/cluster-form/cluster-form.component';
import { AssetFormComponent } from './assets/asset-form/asset-form.component';

const routes: Routes = [
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: 'cluster-editor', component: ClusterEditorComponent },
  { path: 'cluster-editor/:id', component: ClusterEditorComponent },
  { path: 'home', component: HomeComponent },
  { path: 'clusters', component: ClusterListComponent },
  { path: 'assets', component: AssetListComponent },
  { path: 'cluster-form', component: ClusterFormComponent },
  { path: 'cluster-form/:id', component: ClusterFormComponent },
  { path: 'asset-form', component: AssetFormComponent },
  { path: 'asset-form/:id', component: AssetFormComponent }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
