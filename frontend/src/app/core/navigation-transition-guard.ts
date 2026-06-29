import { DOCUMENT } from '@angular/common';
import { afterNextRender, inject, Injectable, Injector } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class NavigationTransitionGuard {
  private readonly root = inject(DOCUMENT).documentElement;
  private readonly injector = inject(Injector);

  constructor() {
    inject(Router).events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        this.root.classList.add('route-settling');
        afterNextRender(() => this.release(), { injector: this.injector });
      }
    });
  }

  private release(): void {
    getComputedStyle(this.root).getPropertyValue('opacity');
    setTimeout(() => this.root.classList.remove('route-settling'), 1);
  }
}
