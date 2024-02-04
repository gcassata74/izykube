import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class IconService {
  private icons: { [key: string]: string } = {};

  // Define a configuration object with icon names and file paths
  private iconConfig = {
    'ingress': 'assets/images/diagram/ingress.svg',
    'pod': 'assets/images/diagram/pod.svg',
    'service': 'assets/images/diagram/service.svg',
    'configMap': 'assets/images/diagram/config-map.svg',
    'container': 'assets/images/diagram/container.svg',
    'deployment': 'assets/images/diagram/deployment.svg',
    // Add more entries as needed
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
