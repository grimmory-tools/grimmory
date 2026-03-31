import { DOCUMENT } from '@angular/common';
import { effect, inject, Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class LayoutService {
  private readonly document = inject(DOCUMENT);

  readonly scale = signal(14);
  readonly sidebarOpen = signal(true);
  readonly mobileSidebarOpen = signal(false);

  constructor() {
    effect(() => {
      this.changeScale(this.scale());
    });
  }

  onMenuToggle(): void {
    if (this.isDesktop()) {
      this.sidebarOpen.update((value) => !value);
    } else {
      this.mobileSidebarOpen.update((value) => !value);
    }
  }

  closeMobileSidebar(): void {
    this.mobileSidebarOpen.set(false);
  }

  isDesktop(): boolean {
    return (this.document.defaultView?.innerWidth ?? 992) > 991;
  }

  private changeScale(value: number): void {
    this.document.documentElement.style.fontSize = `${value}px`;
  }
}
