import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class IconService {
  private icons: { [key: string]: string } = {};

  // Define a configuration object with icon names and file paths
  private iconConfig = {
    'ingress': 'pi pi-globe',
    'service': 'pi pi-share-alt',
    'configmap': 'pi pi-clone',
    'configbundle': 'pi pi-clone',
    'secret': 'pi pi-lock',
    'container': 'pi pi-box',
    'deployment': 'pi pi-sitemap',
    'serviceaccount': 'pi pi-id-card',
    'volume': 'pi pi-database',
    'job': 'pi pi-refresh',
    'istio': 'assets/images/diagram/istio.svg'
  };

  constructor() {
    // Initialize the icons map with the configuration object
    this.icons = { ...this.iconConfig };
  }

  // Retrieve the file path for a given image name
  getIconPath(name: string): string {
    return this.icons[name] || '';
  }
}
