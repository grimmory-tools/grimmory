import { computed, Directive, ElementRef, inject, input } from '@angular/core';

import { AppMenuComponent } from './app-menu.component';

@Directive({
  selector: '[appMenuTriggerFor]',
  standalone: true,
  host: {
    '[attr.aria-haspopup]': "'menu'",
    '[attr.aria-expanded]': 'expanded()',
    '[attr.aria-controls]': 'controls()',
    '(click)': 'toggle($event)',
    '(keydown)': 'onKeydown($event)',
  },
})
export class AppMenuTriggerDirective {
  readonly menu = input.required<AppMenuComponent>({ alias: 'appMenuTriggerFor' });

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly expanded = computed(() =>
    this.menu().openerElement() === this.host.nativeElement ? 'true' : 'false',
  );
  protected readonly controls = computed(() =>
    this.expanded() === 'true' ? this.menu().menu.id() : null,
  );

  protected toggle(event: Event): void {
    event.stopPropagation();
    const menu = this.menu();
    if (menu.openerElement() === this.host.nativeElement) {
      menu.close();
    } else {
      menu.open(this.host.nativeElement);
    }
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp' && event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault();
    event.stopPropagation();
    this.menu().open(this.host.nativeElement, event.key === 'ArrowUp' ? 'last' : 'first');
  }
}

@Directive({
  selector: '[appContextMenuFor]',
  standalone: true,
  host: { '(contextmenu)': 'onContextMenu($event)' },
})
export class AppContextMenuDirective {
  readonly menu = input.required<AppMenuComponent>({ alias: 'appContextMenuFor' });

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected onContextMenu(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    let x = event.clientX;
    let y = event.clientY;
    const fromPointer = !(x === 0 && y === 0);
    if (!fromPointer) {
      const target = event.target instanceof Element ? event.target : this.host.nativeElement;
      const rect = target.getBoundingClientRect();
      x = rect.left + rect.width / 2;
      y = rect.top + rect.height / 2;
    }
    this.menu().openAt(x, y, this.host.nativeElement, fromPointer);
  }
}
