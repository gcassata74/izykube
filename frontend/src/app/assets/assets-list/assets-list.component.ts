import { Component } from '@angular/core';

@Component({
  selector: 'app-assets-list',
  templateUrl: './assets-list.component.html'
})
export class AssetsListComponent {
onEdit(_t19: any) {
throw new Error('Method not implemented.');
}

  assets = [
    { name: 'keycloak', assetType: 'SERVER', referenceType: 'IMAGE', reference: 'Ubuntu_Server_20_04_LTS', physical: 'Yes' }
  ];

  addAsset() {
    // Logic to add a new asset
  }

}
