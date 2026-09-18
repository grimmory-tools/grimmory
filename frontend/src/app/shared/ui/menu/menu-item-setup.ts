import { afterEveryRender, ElementRef, inject, type Signal } from '@angular/core';

import { AppMenuComponent } from './app-menu.component';
import { AppMenuAriaItemDirective } from './menu-aria-item.directive';

export function setupMenuItem(searchLabel: Signal<string>): void {
  const menuItem = inject(AppMenuAriaItemDirective);
  const host = inject<ElementRef<HTMLElement>>(ElementRef);
  menuItem.owner = inject(AppMenuComponent);

  afterEveryRender(() => {
    const label = host.nativeElement.querySelector('[data-menu-label]') ?? host.nativeElement;
    const text = searchLabel().trim() || (label.textContent ?? '').trim();
    menuItem.searchTerm.set(text);
  });
}
