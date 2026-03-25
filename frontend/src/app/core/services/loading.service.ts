import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class LoadingService {
  private activeLoaders: HTMLElement[] = [];

  show(message: string = 'Loading...'): HTMLElement {
    const loader = document.createElement('div');
    loader.className = 'fullscreen-loader';
    loader.innerHTML = `
      <div class="loader-content">
        <i class="pi pi-spin pi-spinner" style="font-size: 3rem; color: var(--primary-color);"></i>
        <p style="margin-top: 1rem; color: var(--text-color);">${message}</p>
      </div>
    `;
    loader.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.7);
      display: flex;
      justify-content: center;
      align-items: center;
      z-index: 9999;
      backdrop-filter: blur(4px);
    `;

    const content = loader.querySelector('.loader-content') as HTMLElement;
    if (content) {
      content.style.cssText = `
        text-align: center;
        background: var(--surface-card);
        padding: 2rem;
        border-radius: 8px;
        box-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
      `;
    }

    document.body.appendChild(loader);
    document.body.style.cursor = 'wait';
    this.activeLoaders.push(loader);

    return loader;
  }

  hide(loader: HTMLElement): void {
    if (loader && loader.parentNode) {
      loader.parentNode.removeChild(loader);

      const index = this.activeLoaders.indexOf(loader);
      if (index > -1) {
        this.activeLoaders.splice(index, 1);
      }

      if (this.activeLoaders.length === 0) {
        document.body.style.cursor = 'default';
      }
    }
  }

  hideAll(): void {
    this.activeLoaders.forEach(loader => {
      if (loader && loader.parentNode) {
        loader.parentNode.removeChild(loader);
      }
    });
    this.activeLoaders = [];
    document.body.style.cursor = 'default';
  }
}

