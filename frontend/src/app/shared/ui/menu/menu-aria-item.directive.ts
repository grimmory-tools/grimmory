import { Directive, forwardRef } from '@angular/core';
import { MenuItem } from '@angular/aria/menu';

import type { AppMenuComponent } from './app-menu.component';

@Directive({
  selector: '[appMenuAriaItem]',
  standalone: true,
  providers: [{ provide: MenuItem, useExisting: forwardRef(() => AppMenuAriaItemDirective) }],
})
export class AppMenuAriaItemDirective extends MenuItem<unknown> {
  owner: AppMenuComponent | undefined;
}
