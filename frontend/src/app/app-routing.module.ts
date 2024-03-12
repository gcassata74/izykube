import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { ClusterListComponent } from './cluster/cluster-list/cluster-list.component';
import { AssetsListComponent } from './assets/assets-list/assets-list.component';
import { ClusterEditorComponent } from './cluster/cluster-editor/cluster-editor.component';
import { ClusterFormComponent } from './cluster/cluster-form/cluster-form.component';

const routes: Routes = [
  { path: '', redirectTo: '/cluster-editor', pathMatch: 'full' },
  { path: 'cluster-editor', component: ClusterEditorComponent },
  { path: 'clusters', component: ClusterListComponent },
  { path: 'assets', component: AssetsListComponent },
  { path: 'cluster-form', component: ClusterFormComponent },
  { path: 'cluster-form/:id', component: ClusterFormComponent }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
