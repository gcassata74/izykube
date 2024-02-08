import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { ClusterListComponent } from './cluster/cluster-list/cluster-list.component';
import { AssetsListComponent } from './assets/assets-list/assets-list.component';
import { ClusterEditorComponent } from './cluster/cluster-editor/cluster-editor.component';

const routes: Routes = [
  { path: '', redirectTo: '/cluster', pathMatch: 'full' },
  { path: 'cluster', component: ClusterEditorComponent },
  { path: 'clusters', component: ClusterListComponent },
  { path: 'assets', component: AssetsListComponent }
  // Add more routes here if needed
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
