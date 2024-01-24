import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class IconService {

  constructor() { }

  // Function to convert an icon to a Base64 image

  convertIconToBase64(iconClass: string): string {
    // Create a canvas element
    const canvas = document.createElement('canvas');
    canvas.width = 100;
    canvas.height = 100;
  
    // Draw the icon onto the canvas using the font icon style
    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.fillStyle = '#000000';
      ctx.font = 'normal 24px PrimeIcons';
      ctx.textBaseline = 'middle';
      ctx.textAlign = 'center';
      ctx.fillText(iconClass, canvas.width / 2, canvas.height / 2);
    }
  
    // Convert the canvas to a data URL and return the base64 representation
    return canvas.toDataURL();
  }
}
